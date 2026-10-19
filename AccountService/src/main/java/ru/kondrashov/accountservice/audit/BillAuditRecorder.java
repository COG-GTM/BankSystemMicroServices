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
    private static final String APPLICATION_PACKAGE = "ru.kondrashov.accountservice.";

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

        Bill bill = findBill(billId);
        Bill counterpartyBill = findBill(counterpartyBillId);
        BigDecimal balanceBefore = bill == null ? null : bill.getAmount();
        BigDecimal counterpartyBalanceBefore = counterpartyBill == null ? null : counterpartyBill.getAmount();
        event.balanceBefore(balanceBefore)
                .balanceBeforeCurrency(currencyCode(bill))
                .counterpartyBalanceBefore(counterpartyBalanceBefore)
                .counterpartyBalanceCurrency(currencyCode(counterpartyBill));
        recordOwner(event, bill);

        UUID transactionId;
        try {
            transactionId = operation.execute();
        } catch (RuntimeException e) {
            event.outcome(e instanceof AuditRejectedException ? AuditOutcome.REJECTED : AuditOutcome.FAILED)
                    .failureReason(reason(e))
                    .balanceAfterCurrency(currencyCode(bill))
                    .balanceAfter(balanceBefore)
                    .counterpartyBalanceAfter(counterpartyBalanceBefore);
            auditService.recordFailure(event.build());
            throw e;
        }

        event.outcome(AuditOutcome.SUCCESS).transactionId(transactionId);
        if (billId != null) {
            Bill billAfter = findBill(billId);
            event.balanceAfter(billAfter == null ? null : billAfter.getAmount())
                    .balanceAfterCurrency(currencyCode(billAfter));
            recordOwner(event, billAfter);
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
        Bill bill = findBill(billId);
        return bill == null ? null : bill.getAmount();
    }

    private Bill findBill(UUID billId) {
        if (billId == null) {
            return null;
        }
        try {
            return billsService.getBill(billId);
        } catch (BillNotFoundException e) {
            return null;
        }
    }

    /** Uses the bill's real owner instead of the (unverified) path ids. */
    public static void recordOwner(AuditEvent.AuditEventBuilder event, Bill bill) {
        if (bill != null && bill.getAccount() != null && bill.getAccount().getId() != null) {
            event.accountId(bill.getAccount().getId()).personId(bill.getAccount().getPersonId());
        }
    }

    public static String currencyCode(Bill bill) {
        return bill == null || bill.getCurrency() == null ? null : bill.getCurrency().getCurrencyCode();
    }

    /** Keeps messages of the service's own exceptions only; framework/driver messages may leak internals. */
    private static String reason(RuntimeException e) {
        boolean ownException = e.getClass().getName().startsWith(APPLICATION_PACKAGE);
        String reason = e.getClass().getSimpleName() + (!ownException || e.getMessage() == null ? "" : ": " + e.getMessage());
        return reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    }
}
