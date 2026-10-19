package ru.kondrashov.personservice.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Writes an audit event for every person create/update/delete to the append-only
 * {@code person_audit_events} table and to the {@value #AUDIT_LOGGER} log.
 */
@Service
public class PersonAuditService {

    public static final String AUDIT_LOGGER = "ru.kondrashov.personservice.AUDIT";

    private static final Logger auditLog = LogManager.getLogger(AUDIT_LOGGER);

    private final PersonAuditRepository auditRepository;
    private final AuditActorResolver actorResolver;
    private final Clock clock;

    @Autowired
    public PersonAuditService(PersonAuditRepository auditRepository, AuditActorResolver actorResolver) {
        this(auditRepository, actorResolver, Clock.systemUTC());
    }

    PersonAuditService(PersonAuditRepository auditRepository, AuditActorResolver actorResolver, Clock clock) {
        this.auditRepository = auditRepository;
        this.actorResolver = actorResolver;
        this.clock = clock;
    }

    /** Joins the caller's transaction so the event commits or rolls back with the change itself. */
    @Transactional(propagation = Propagation.REQUIRED)
    public PersonAuditEvent recordSuccess(AuditOperation operation, UUID personId, Collection<String> changedFields) {
        return record(operation, personId, changedFields, AuditOutcome.SUCCESS, null);
    }

    /** Commits independently so the failed attempt is kept when the caller's transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersonAuditEvent recordFailure(AuditOperation operation, UUID personId, Collection<String> changedFields, Throwable failure) {
        return record(operation, personId, changedFields, AuditOutcome.FAILURE, failure.getClass().getSimpleName());
    }

    private PersonAuditEvent record(AuditOperation operation, UUID personId, Collection<String> changedFields,
                                    AuditOutcome outcome, String failureReason) {
        PersonAuditEvent event = PersonAuditEvent.builder()
                .occurredAt(Instant.now(clock))
                .actor(actorResolver.currentActor())
                .clientAddress(actorResolver.currentClientAddress())
                .operation(operation)
                .personId(personId)
                .changedFields(changedFields == null || changedFields.isEmpty() ? null : String.join(",", changedFields))
                .outcome(outcome)
                .failureReason(failureReason)
                .build();
        PersonAuditEvent saved = auditRepository.save(event);
        auditLog.info("event=person.audit operation={} personId={} actor={} clientAddress={} changedFields=[{}] outcome={} failureReason={} occurredAt={}",
                event.getOperation(), event.getPersonId(), event.getActor(), event.getClientAddress(),
                event.getChangedFields() == null ? "" : event.getChangedFields(),
                event.getOutcome(), event.getFailureReason(), event.getOccurredAt());
        return saved;
    }
}
