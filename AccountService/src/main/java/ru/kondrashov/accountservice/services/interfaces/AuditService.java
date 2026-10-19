package ru.kondrashov.accountservice.services.interfaces;

import ru.kondrashov.accountservice.entities.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditService {

    /** Persists the event in the caller's transaction, so it commits or rolls back with the operation. */
    AuditEvent recordSuccess(AuditEvent auditEvent);

    /** Persists the event in its own transaction, so it survives the rollback of the failed operation. */
    AuditEvent recordFailure(AuditEvent auditEvent);

    /**
     * Returns the audit trail of a bill owned by the given person and account, including after the bill
     * has been deleted. Throws BillNotFoundException if the bill does not belong to them.
     */
    List<AuditEvent> getEventsByBillId(UUID personId, UUID accountId, UUID billId);
}
