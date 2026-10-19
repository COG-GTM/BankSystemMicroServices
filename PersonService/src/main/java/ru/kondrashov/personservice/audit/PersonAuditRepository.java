package ru.kondrashov.personservice.audit;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Deliberately exposes no update or delete operations: audit events are append-only.
 */
@org.springframework.stereotype.Repository
public interface PersonAuditRepository extends Repository<PersonAuditEvent, UUID> {

    PersonAuditEvent save(PersonAuditEvent event);

    List<PersonAuditEvent> findByPersonIdOrderByOccurredAtAsc(UUID personId);

    List<PersonAuditEvent> findAllByOrderByOccurredAtAsc();
}
