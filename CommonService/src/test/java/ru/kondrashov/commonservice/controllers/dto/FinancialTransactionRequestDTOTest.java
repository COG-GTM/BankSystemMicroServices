package ru.kondrashov.commonservice.controllers.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionRequestDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void missingTimeStaysNull() throws Exception {
        FinancialTransactionRequestDTO dto = objectMapper.readValue(
                "{\"id\":\"6f1c2a52-6d59-4b6e-9a43-0d1f6f8f1a10\",\"currency\":\"USD\",\"amount\":10.00,\"message\":\"payment\"}",
                FinancialTransactionRequestDTO.class);

        assertThat(dto.getTime()).isNull();
        assertThat(dto.getCurrency()).isEqualTo(Currency.getInstance("USD"));
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void providedTimeIsKept() throws Exception {
        FinancialTransactionRequestDTO dto = objectMapper.readValue(
                "{\"currency\":\"EUR\",\"amount\":5,\"time\":\"2021-03-04T05:06:07\",\"message\":\"transfer\"}",
                FinancialTransactionRequestDTO.class);

        assertThat(dto.getTime()).isEqualTo(LocalDateTime.of(2021, 3, 4, 5, 6, 7));
    }

}
