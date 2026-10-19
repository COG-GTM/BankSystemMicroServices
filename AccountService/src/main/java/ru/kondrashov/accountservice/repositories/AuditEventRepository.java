package ru.kondrashov.accountservice.repositories;

import org.springframework.data.repository.Repository;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.AuditOutcome;
import ru.kondrashov.accountservice.entities.AuditEvent;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately exposes no update or delete methods: audit events are append-only.
 */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent auditEvent);

    List<AuditEvent> findByBillIdOrCounterpartyBillIdOrderByOccurredAtAsc(UUID billId, UUID counterpartyBillId);

    boolean existsByBillIdAndPersonIdAndAccountIdAndOperationAndOutcome(UUID billId, UUID personId, UUID accountId,
                                                                         AuditOperation operation, AuditOutcome outcome);
}
