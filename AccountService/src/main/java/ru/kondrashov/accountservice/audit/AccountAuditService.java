package ru.kondrashov.accountservice.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.kondrashov.accountservice.entities.Account;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes a structured audit record (DB row + JSON line on the AUDIT logger) for every account mutation.
 */
@Service
public class AccountAuditService {

    public static final String AUDIT_LOGGER_NAME = "AUDIT";
    private static final Logger AUDIT_LOG = LogManager.getLogger(AUDIT_LOGGER_NAME);
    static final int MAX_NAME_IN_SNAPSHOT = 1000;
    public static final String ROLLED_BACK_REASON = "Transaction rolled back after the mutation was applied";

    private final AccountAuditRepository repository;
    private final AuditActorResolver actorResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    @Autowired
    public AccountAuditService(AccountAuditRepository repository, AuditActorResolver actorResolver,
                               PlatformTransactionManager transactionManager) {
        this(repository, actorResolver, Clock.systemUTC(), transactionManager);
    }

    AccountAuditService(AccountAuditRepository repository, AuditActorResolver actorResolver, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
        this.newTransaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (this.newTransaction != null) {
            this.newTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        }
    }

    public static Map<String, Object> snapshot(Account account) {
        if (account == null) {
            return null;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", account.getId() == null ? null : account.getId().toString());
        state.put("personId", account.getPersonId() == null ? null : account.getPersonId().toString());
        state.put("name", account.getName() == null || account.getName().length() <= MAX_NAME_IN_SNAPSHOT
                ? account.getName()
                : account.getName().substring(0, MAX_NAME_IN_SNAPSHOT) + "...[truncated]");
        state.put("creationDate", account.getCreationDate() == null ? null : account.getCreationDate().toString());
        return state;
    }

    /**
     * Joins the caller's transaction so the record commits or rolls back with the mutation.
     * If that transaction later rolls back, a FAILURE record is written in a new transaction instead.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AccountAuditEvent recordSuccess(AuditAction action, UUID accountId,
                                           Map<String, Object> before, Map<String, Object> after) {
        AccountAuditEvent event = repository.save(build(action, AuditOutcome.SUCCESS, accountId, before, after, null));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            AUDIT_LOG.info(toLogLine(event, before, after, AuditOutcome.SUCCESS.name()));
            return event;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    AUDIT_LOG.info(toLogLine(event, before, after, AuditOutcome.SUCCESS.name()));
                } else {
                    recordRolledBack(event, before, after);
                }
            }
        });
        return event;
    }

    /**
     * Runs in its own transaction so the failure is recorded even though the mutation rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountAuditEvent recordFailure(AuditAction action, UUID accountId,
                                           Map<String, Object> before, Map<String, Object> attempted,
                                           Throwable cause) {
        return persistFailure(build(action, AuditOutcome.FAILURE, accountId, before, attempted, describe(cause)),
                before, attempted);
    }

    private AccountAuditEvent persistFailure(AccountAuditEvent event, Map<String, Object> before,
                                             Map<String, Object> attempted) {
        AccountAuditEvent saved;
        try {
            saved = repository.save(event);
        } catch (RuntimeException e) {
            AUDIT_LOG.error(toLogLine(event, before, attempted, AuditOutcome.FAILURE.name(), false));
            throw e;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            AUDIT_LOG.warn(toLogLine(saved, before, attempted, AuditOutcome.FAILURE.name()));
            return saved;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean persisted = status == STATUS_COMMITTED;
                String line = toLogLine(saved, before, attempted, AuditOutcome.FAILURE.name(), persisted);
                if (persisted) {
                    AUDIT_LOG.warn(line);
                } else {
                    AUDIT_LOG.error(line);
                }
            }
        });
        return saved;
    }

    private void recordRolledBack(AccountAuditEvent success, Map<String, Object> before, Map<String, Object> after) {
        AccountAuditEvent failure = AccountAuditEvent.builder()
                .occurredAt(Instant.now(clock))
                .action(success.getAction())
                .outcome(AuditOutcome.FAILURE)
                .actor(success.getActor())
                .actorSource(success.getActorSource())
                .clientAddress(success.getClientAddress())
                .requestId(success.getRequestId())
                .accountId(success.getAccountId())
                .personId(success.getPersonId())
                .beforeState(success.getBeforeState())
                .afterState(success.getAfterState())
                .failureReason(ROLLED_BACK_REASON)
                .build();
        AtomicBoolean attempted = new AtomicBoolean(false);
        try {
            if (newTransaction == null) {
                attempted.set(true);
                persistFailure(failure, before, after);
            } else {
                newTransaction.executeWithoutResult(status -> {
                    attempted.set(true);
                    persistFailure(failure, before, after);
                });
            }
        } catch (RuntimeException e) {
            if (!attempted.get()) {
                AUDIT_LOG.error(toLogLine(failure, before, after, AuditOutcome.FAILURE.name(), false));
            }
        }
    }

    private AccountAuditEvent build(AuditAction action, AuditOutcome outcome, UUID accountId,
                                    Map<String, Object> before, Map<String, Object> after, String failureReason) {
        AuditActor actor = actorResolver.resolve();
        return AccountAuditEvent.builder()
                .occurredAt(Instant.now(clock))
                .action(action)
                .outcome(outcome)
                .actor(actor.getName())
                .actorSource(actor.getSource())
                .clientAddress(actor.getClientAddress())
                .requestId(actor.getRequestId())
                .accountId(accountId)
                .personId(personIdOf(after != null ? after : before))
                .beforeState(toJson(before))
                .afterState(toJson(after))
                .failureReason(failureReason)
                .build();
    }

    private String toLogLine(AccountAuditEvent event, Map<String, Object> before, Map<String, Object> after,
                             String outcome) {
        return toLogLine(event, before, after, outcome, true);
    }

    private String toLogLine(AccountAuditEvent event, Map<String, Object> before, Map<String, Object> after,
                             String outcome, boolean persisted) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("timestamp", event.getOccurredAt().toString());
        line.put("eventId", event.getId() == null ? null : event.getId().toString());
        line.put("resource", "account");
        line.put("action", event.getAction().name());
        line.put("outcome", outcome);
        line.put("actor", event.getActor());
        line.put("actorSource", event.getActorSource().name());
        line.put("clientAddress", event.getClientAddress());
        line.put("requestId", event.getRequestId());
        line.put("accountId", event.getAccountId() == null ? null : event.getAccountId().toString());
        line.put("personId", event.getPersonId() == null ? null : event.getPersonId().toString());
        line.put("before", before);
        line.put("after", after);
        line.put("failureReason", event.getFailureReason());
        line.put("persisted", persisted);
        String json = toJson(line);
        return json == null ? "{}" : json;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise audit state", e);
        }
    }

    private static UUID personIdOf(Map<String, Object> state) {
        Object personId = state == null ? null : state.get("personId");
        return personId == null ? null : UUID.fromString(personId.toString());
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return null;
        }
        String message = cause.getMessage();
        String reason = cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return AuditActorResolver.sanitize(reason, AccountAuditEvent.REASON_LENGTH);
    }
}
