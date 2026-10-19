package ru.kondrashov.accountservice.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.kondrashov.accountservice.entities.Account;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a structured audit record (DB row + JSON line on the AUDIT logger) for every account mutation.
 */
@Service
public class AccountAuditService {

    public static final String AUDIT_LOGGER_NAME = "AUDIT";
    private static final Logger AUDIT_LOG = LogManager.getLogger(AUDIT_LOGGER_NAME);

    private final AccountAuditRepository repository;
    private final AuditActorResolver actorResolver;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AccountAuditService(AccountAuditRepository repository, AuditActorResolver actorResolver) {
        this(repository, actorResolver, Clock.systemUTC());
    }

    AccountAuditService(AccountAuditRepository repository, AuditActorResolver actorResolver, Clock clock) {
        this.repository = repository;
        this.actorResolver = actorResolver;
        this.objectMapper = new ObjectMapper();
        this.clock = clock;
    }

    public static Map<String, Object> snapshot(Account account) {
        if (account == null) {
            return null;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", account.getId() == null ? null : account.getId().toString());
        state.put("personId", account.getPersonId() == null ? null : account.getPersonId().toString());
        state.put("name", account.getName());
        state.put("creationDate", account.getCreationDate() == null ? null : account.getCreationDate().toString());
        return state;
    }

    /**
     * Joins the caller's transaction so the record commits or rolls back with the mutation.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AccountAuditEvent recordSuccess(AuditAction action, UUID accountId,
                                           Map<String, Object> before, Map<String, Object> after) {
        AccountAuditEvent event = repository.save(build(action, AuditOutcome.SUCCESS, accountId, before, after, null));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        AUDIT_LOG.info(toLogLine(event, before, after, AuditOutcome.SUCCESS.name()));
                    } else {
                        AUDIT_LOG.warn(toLogLine(event, before, after, "ROLLED_BACK"));
                    }
                }
            });
        } else {
            AUDIT_LOG.info(toLogLine(event, before, after, AuditOutcome.SUCCESS.name()));
        }
        return event;
    }

    /**
     * Runs in its own transaction so the failure is recorded even though the mutation rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountAuditEvent recordFailure(AuditAction action, UUID accountId,
                                           Map<String, Object> before, Map<String, Object> attempted,
                                           Throwable cause) {
        AccountAuditEvent event = build(action, AuditOutcome.FAILURE, accountId, before, attempted, describe(cause));
        AUDIT_LOG.warn(toLogLine(event, before, attempted, AuditOutcome.FAILURE.name()));
        return repository.save(event);
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
                .beforeState(truncate(toJson(before), AccountAuditEvent.STATE_LENGTH))
                .afterState(truncate(toJson(after), AccountAuditEvent.STATE_LENGTH))
                .failureReason(failureReason)
                .build();
    }

    private String toLogLine(AccountAuditEvent event, Map<String, Object> before, Map<String, Object> after,
                             String outcome) {
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

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
