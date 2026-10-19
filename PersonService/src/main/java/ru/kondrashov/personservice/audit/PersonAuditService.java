package ru.kondrashov.personservice.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    public static final String ROLLED_BACK_AFTER_SUCCESS = "TransactionRolledBack";

    private static final Logger auditLog = LogManager.getLogger(AUDIT_LOGGER);

    private final PersonAuditRepository auditRepository;
    private final AuditActorResolver actorResolver;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    @Autowired
    public PersonAuditService(PersonAuditRepository auditRepository, AuditActorResolver actorResolver,
                              PlatformTransactionManager transactionManager) {
        this(auditRepository, actorResolver, transactionManager, Clock.systemUTC());
    }

    PersonAuditService(PersonAuditRepository auditRepository, AuditActorResolver actorResolver,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.auditRepository = auditRepository;
        this.actorResolver = actorResolver;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Joins the caller's transaction so the event commits or rolls back with the change itself.
     * If that transaction later rolls back (e.g. a failed flush at commit), a FAILURE event is
     * recorded in a new transaction instead.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public PersonAuditEvent recordSuccess(AuditOperation operation, UUID personId, Collection<String> changedFields) {
        PersonAuditEvent event = record(operation, personId, changedFields, AuditOutcome.SUCCESS, null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        requiresNew.execute(tx -> record(operation, personId, changedFields,
                                AuditOutcome.FAILURE, ROLLED_BACK_AFTER_SUCCESS, event.getActor(), event.getClientAddress()));
                    }
                }
            });
        }
        return event;
    }

    /** Commits independently so the failed attempt is kept when the caller's transaction rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PersonAuditEvent recordFailure(AuditOperation operation, UUID personId, Collection<String> changedFields, Throwable failure) {
        return record(operation, personId, changedFields, AuditOutcome.FAILURE, failure.getClass().getSimpleName());
    }

    private PersonAuditEvent record(AuditOperation operation, UUID personId, Collection<String> changedFields,
                                    AuditOutcome outcome, String failureReason) {
        return record(operation, personId, changedFields, outcome, failureReason,
                actorResolver.currentActor(), actorResolver.currentClientAddress());
    }

    private PersonAuditEvent record(AuditOperation operation, UUID personId, Collection<String> changedFields,
                                    AuditOutcome outcome, String failureReason, String actor, String clientAddress) {
        PersonAuditEvent event = PersonAuditEvent.builder()
                .occurredAt(Instant.now(clock))
                .actor(actor)
                .clientAddress(clientAddress)
                .operation(operation)
                .personId(personId)
                .changedFields(changedFields == null || changedFields.isEmpty() ? null : String.join(",", changedFields))
                .outcome(outcome)
                .failureReason(failureReason)
                .build();
        PersonAuditEvent saved = auditRepository.save(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log(event);
                }
            });
        } else {
            log(event);
        }
        return saved;
    }

    private static void log(PersonAuditEvent event) {
        auditLog.info("event=person.audit operation={} personId={} actor={} clientAddress={} changedFields=[{}] outcome={} failureReason={} occurredAt={}",
                event.getOperation(), event.getPersonId(), event.getActor(), event.getClientAddress(),
                event.getChangedFields() == null ? "" : event.getChangedFields(),
                event.getOutcome(), event.getFailureReason(), event.getOccurredAt());
    }
}
