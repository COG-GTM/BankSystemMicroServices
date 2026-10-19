package ru.kondrashov.accountservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.AuditOutcome;
import ru.kondrashov.accountservice.entities.AuditEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditEventResponseDTO {

    private UUID id;
    private Instant occurredAt;
    private AuditOperation operation;
    private AuditOutcome outcome;
    private String failureReason;
    private String actor;
    private String approvedBy;
    private UUID personId;
    private UUID accountId;
    private UUID billId;
    private UUID counterpartyBillId;
    private UUID transactionId;
    private BigDecimal amount;
    private String currency;
    private String balanceBeforeCurrency;
    private BigDecimal balanceBefore;
    private String balanceAfterCurrency;
    private BigDecimal balanceAfter;
    private String counterpartyBalanceCurrency;
    private BigDecimal counterpartyBalanceBefore;
    private BigDecimal counterpartyBalanceAfter;
    private String requestId;

    public static AuditEventResponseDTO from(AuditEvent event) {
        return new AuditEventResponseDTO(
                event.getId(),
                event.getOccurredAt(),
                event.getOperation(),
                event.getOutcome(),
                event.getFailureReason(),
                event.getActor(),
                event.getApprovedBy(),
                event.getPersonId(),
                event.getAccountId(),
                event.getBillId(),
                event.getCounterpartyBillId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getCurrency(),
                event.getBalanceBeforeCurrency(),
                event.getBalanceBefore(),
                event.getBalanceAfterCurrency(),
                event.getBalanceAfter(),
                event.getCounterpartyBalanceCurrency(),
                event.getCounterpartyBalanceBefore(),
                event.getCounterpartyBalanceAfter(),
                event.getRequestId()
        );
    }

    /**
     * View of an event as seen from {@code viewedBillId}. When that bill is only the counterparty (e.g. the
     * beneficiary of a transfer), the initiating side's identifiers, balances, actor and reason are withheld.
     */
    public static AuditEventResponseDTO from(AuditEvent event, UUID viewedBillId) {
        AuditEventResponseDTO dto = from(event);
        if (viewedBillId != null && viewedBillId.equals(event.getCounterpartyBillId()) && !viewedBillId.equals(event.getBillId())) {
            dto.setPersonId(null);
            dto.setAccountId(null);
            dto.setBillId(null);
            dto.setActor(null);
            dto.setApprovedBy(null);
            dto.setFailureReason(null);
            dto.setBalanceBeforeCurrency(null);
            dto.setBalanceBefore(null);
            dto.setBalanceAfterCurrency(null);
            dto.setBalanceAfter(null);
            dto.setRequestId(null);
        }
        return dto;
    }
}
