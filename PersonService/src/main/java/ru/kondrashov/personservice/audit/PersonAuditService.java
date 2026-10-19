package ru.kondrashov.personservice.audit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.kondrashov.personservice.entities.Person;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/**
 * Writes an audit event for every create, update and delete of a person. Personal data is
 * never stored in clear: updates keep masked before/after values, and deletions keep only
 * the list of erased fields so the audit trail does not undo the erasure.
 */
@Service
public class PersonAuditService {

    private static final Logger auditLogger = LogManager.getLogger("ru.kondrashov.personservice.audit");

    private static final Map<String, Function<Person, Object>> AUDITED_FIELDS;

    static {
        Map<String, Function<Person, Object>> fields = new LinkedHashMap<>();
        fields.put("firstName", Person::getFirstName);
        fields.put("lastName", Person::getLastName);
        fields.put("birthdate", Person::getBirthdate);
        fields.put("phoneNumber", Person::getPhoneNumber);
        fields.put("email", Person::getEmail);
        AUDITED_FIELDS = Collections.unmodifiableMap(fields);
    }

    private final PersonAuditRepository personAuditRepository;
    private final AuditActorProvider auditActorProvider;
    private final Clock clock;

    @Autowired
    public PersonAuditService(PersonAuditRepository personAuditRepository, AuditActorProvider auditActorProvider) {
        this(personAuditRepository, auditActorProvider, Clock.systemUTC());
    }

    PersonAuditService(PersonAuditRepository personAuditRepository, AuditActorProvider auditActorProvider, Clock clock) {
        this.personAuditRepository = personAuditRepository;
        this.auditActorProvider = auditActorProvider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCreate(Person created) {
        List<String> fields = new ArrayList<>();
        AUDITED_FIELDS.forEach((name, getter) -> {
            if (getter.apply(created) != null) {
                fields.add(name);
            }
        });
        record(created.getId(), PersonAuditAction.CREATE, fields, null);
    }

    /**
     * @param before snapshot of the person taken before the change was applied
     * @param after  the person after the change
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpdate(Person before, Person after) {
        List<String> fields = new ArrayList<>();
        StringJoiner changes = new StringJoiner("; ");
        AUDITED_FIELDS.forEach((name, getter) -> {
            Object oldValue = getter.apply(before);
            Object newValue = getter.apply(after);
            if (!Objects.equals(oldValue, newValue)) {
                fields.add(name);
                changes.add(name + ": " + mask(oldValue) + " -> " + mask(newValue));
            }
        });
        if (fields.isEmpty()) {
            return;
        }
        record(after.getId(), PersonAuditAction.UPDATE, fields, changes.toString());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDelete(Person deleted) {
        record(deleted.getId(), PersonAuditAction.DELETE, new ArrayList<>(AUDITED_FIELDS.keySet()), null);
    }

    private void record(UUID personId, PersonAuditAction action, List<String> fields, String changes) {
        PersonAuditEvent event = new PersonAuditEvent(
                personId,
                action,
                auditActorProvider.currentActor(),
                auditActorProvider.currentSourceAddress(),
                Instant.now(clock),
                String.join(",", fields),
                changes);
        personAuditRepository.save(event);
        Runnable log = () -> auditLogger.info("person_audit action={} personId={} actor={} source={} fields={}",
                action, personId, event.getActor(), event.getSourceAddress(), event.getChangedFields());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.run();
                }
            });
        } else {
            log.run();
        }
    }

    static String mask(Object value) {
        if (value == null) {
            return "null";
        }
        String text = value.toString();
        if (text.length() <= 2) {
            return "***";
        }
        return text.charAt(0) + "***" + text.charAt(text.length() - 1);
    }
}
