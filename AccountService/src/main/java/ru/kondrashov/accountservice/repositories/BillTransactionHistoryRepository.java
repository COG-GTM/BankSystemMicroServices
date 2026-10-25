package ru.kondrashov.accountservice.repositories;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;

import java.sql.Timestamp;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

/**
 * Signed, newest-first transaction history of a bill read as a single projection,
 * so no Payment/Adjustment/Transfer entities (and their EAGER bills) are hydrated.
 * Outflows (payments, outgoing transfers) are negated in SQL.
 */
@Repository
@RequiredArgsConstructor
public class BillTransactionHistoryRepository {

    static final String HISTORY_SQL =
            "SELECT h.id, h.currency, h.amount, h.time, h.message FROM (" +
            " SELECT a.id, a.currency, a.amount AS amount, a.time, a.message FROM public.adjustments a WHERE a.bill_id = :billId" +
            " UNION ALL" +
            " SELECT p.id, p.currency, -p.amount AS amount, p.time, p.message FROM public.payments p WHERE p.bill_id = :billId" +
            " UNION ALL" +
            " SELECT t.id, t.currency, t.amount AS amount, t.time, t.message FROM public.transfers t WHERE t.beneficiary_bill_id = :billId" +
            " UNION ALL" +
            " SELECT t.id, t.currency, -t.amount AS amount, t.time, t.message FROM public.transfers t WHERE t.source_bill_id = :billId" +
            ") h ORDER BY h.time DESC, h.id DESC LIMIT :limit OFFSET :offset";

    private static final RowMapper<FinancialTransactionResponseDTO> ROW_MAPPER = (rs, rowNum) -> {
        Timestamp time = rs.getTimestamp("time");
        String currency = rs.getString("currency");
        return new FinancialTransactionResponseDTO(
                rs.getObject("id", UUID.class),
                currency == null ? null : Currency.getInstance(currency),
                rs.getBigDecimal("amount"),
                time == null ? null : time.toLocalDateTime(),
                rs.getString("message"));
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<FinancialTransactionResponseDTO> findHistory(UUID billId, long offset, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("billId", billId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(HISTORY_SQL, params, ROW_MAPPER);
    }
}
