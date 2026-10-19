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
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
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
                event.getBalanceBefore(),
                event.getBalanceAfter(),
                event.getCounterpartyBalanceBefore(),
                event.getCounterpartyBalanceAfter(),
                event.getRequestId()
        );
    }
}
