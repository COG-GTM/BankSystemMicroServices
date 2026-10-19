package ru.kondrashov.accountservice.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.kondrashov.accountservice.entities.AuditEvent;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.MissingActorException;
import ru.kondrashov.accountservice.exceptions.AuditRejectedException;
import ru.kondrashov.accountservice.services.interfaces.AuditService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wraps a mutating bills operation and writes exactly one audit event for it: SUCCESS in the
 * operation's transaction, or REJECTED/FAILED in a separate transaction before rethrowing.
 */
@Component
@RequiredArgsConstructor
public class BillAuditRecorder {

    public static final String UNIDENTIFIED_ACTOR = "unidentified";
    private static final int MAX_REASON_LENGTH = 512;

    private final BillsService billsService;
    private final AuditService auditService;
    private final AuditContextResolver auditContextResolver;

    @FunctionalInterface
    public interface AuditedOperation {
        /** Runs the operation and returns the id of the created transaction, if any. */
        UUID execute();
    }

    public void record(AuditEvent.AuditEventBuilder event, UUID billId, UUID counterpartyBillId, AuditedOperation operation) {
        event.occurredAt(Instant.now())
                .actor(auditContextResolver.actor().orElse(UNIDENTIFIED_ACTOR))
                .requestId(auditContextResolver.requestId())
                .clientAddress(auditContextResolver.clientAddress())
                .billId(billId)
                .counterpartyBillId(counterpartyBillId);

        BigDecimal balanceBefore = balanceOf(billId);
        BigDecimal counterpartyBalanceBefore = balanceOf(counterpartyBillId);
        event.balanceBefore(balanceBefore).counterpartyBalanceBefore(counterpartyBalanceBefore);

        UUID transactionId;
        try {
            transactionId = operation.execute();
        } catch (RuntimeException e) {
            event.outcome(e instanceof AuditRejectedException ? AuditOutcome.REJECTED : AuditOutcome.FAILED)
                    .failureReason(reason(e))
                    .balanceAfter(balanceBefore)
                    .counterpartyBalanceAfter(counterpartyBalanceBefore);
            auditService.recordFailure(event.build());
            throw e;
        }

        event.outcome(AuditOutcome.SUCCESS).transactionId(transactionId);
        if (billId != null) {
            event.balanceAfter(balanceOf(billId));
        }
        if (counterpartyBillId != null) {
            event.counterpartyBalanceAfter(balanceOf(counterpartyBillId));
        }
        auditService.recordSuccess(event.build());
    }

    public String requireActor() {
        return auditContextResolver.actor().orElseThrow(() -> new MissingActorException(
                "Header " + AuditContextResolver.ACTOR_HEADER + " with the acting user's id is required for this operation"));
    }

    private BigDecimal balanceOf(UUID billId) {
        if (billId == null) {
            return null;
        }
        try {
            Bill bill = billsService.getBill(billId);
            return bill == null ? null : bill.getAmount();
        } catch (BillNotFoundException e) {
            return null;
        }
    }

    private static String reason(RuntimeException e) {
        String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }
}
