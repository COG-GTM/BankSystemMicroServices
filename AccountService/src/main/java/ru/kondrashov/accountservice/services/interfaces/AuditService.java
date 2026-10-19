package ru.kondrashov.accountservice.services.interfaces;

import ru.kondrashov.accountservice.entities.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditService {

    /** Persists the event in the caller's transaction, so it commits or rolls back with the operation. */
    AuditEvent recordSuccess(AuditEvent auditEvent);

    /** Persists the event in its own transaction, so it survives the rollback of the failed operation. */
    AuditEvent recordFailure(AuditEvent auditEvent);

    List<AuditEvent> getEventsByBillId(UUID billId);
}
