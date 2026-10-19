package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.InvalidErasureAuditEventException;
import ru.kondrashov.personservice.exception.PersonErasedException;
import ru.kondrashov.personservice.exception.PersonHasOpenAccountsException;
import ru.kondrashov.personservice.exception.PersonNotFoundException;
import ru.kondrashov.personservice.repositories.ErasureAuditEventRepository;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PeopleServiceImpl implements PeopleService{
    static final String ERASED = "ERASED";
    static final String UNKNOWN_ACTOR = "unknown";

    private final PeopleRepository peopleRepository;
    private final ErasureAuditEventRepository erasureAuditEventRepository;
    private final OpenAccountsVerifier openAccountsVerifier;
    private final Clock clock;

    @Autowired
    public PeopleServiceImpl(PeopleRepository peopleRepository, ErasureAuditEventRepository erasureAuditEventRepository,
                             OpenAccountsVerifier openAccountsVerifier) {
        this(peopleRepository, erasureAuditEventRepository, openAccountsVerifier, Clock.systemUTC());
    }

    PeopleServiceImpl(PeopleRepository peopleRepository, ErasureAuditEventRepository erasureAuditEventRepository,
                      OpenAccountsVerifier openAccountsVerifier, Clock clock) {
        this.peopleRepository = peopleRepository;
        this.erasureAuditEventRepository = erasureAuditEventRepository;
        this.openAccountsVerifier = openAccountsVerifier;
        this.clock = clock;
    }

    public List<Person> getAll(){
        return peopleRepository.findAllByErasedFalse();
    }

    public Person get(UUID id){
        return peopleRepository.
                getPersonById(id).
                orElseThrow( () -> new PersonNotFoundException("Person with id = "+id+" not found"));
    }

    public void save(Person person){
        peopleRepository.save(person);
    }

    public void update(UUID id, Person changedPerson) {
        Person originPerson = get(id);
        if (originPerson.isErased()) {
            throw new PersonErasedException("Person with id = "+id+" has been erased");
        }
        originPerson.setFirstName(changedPerson.getFirstName());
        originPerson.setLastName(changedPerson.getLastName());
        originPerson.setBirthdate(changedPerson.getBirthdate());
        originPerson.setPhoneNumber(changedPerson.getPhoneNumber());
        originPerson.setEmail(changedPerson.getEmail());

        peopleRepository.save(originPerson);

    }

    /**
     * Anonymises the person's PII in place instead of deleting the row, so that
     * retained financial records still reference a resolvable (but anonymous) customer.
     * Refuses while AccountService still reports open accounts for the person, so it can only be the final
     * step after every account has been settled and closed. The COMPLETED audit event is written in the same
     * local transaction as the anonymisation. Idempotent: erasing an already erased person is a no-op.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Person erase(UUID id, String actor, String sourceAddress){
        Person person = get(id);
        if (person.isErased()) {
            return person;
        }
        if (openAccountsVerifier.hasOpenAccounts(id)) {
            throw new PersonHasOpenAccountsException("Person with id = " + id + " still has open accounts; close them before erasure");
        }
        person.setFirstName(ERASED);
        person.setLastName(ERASED);
        person.setBirthdate(null);
        person.setPhoneNumber(ERASED);
        person.setEmail("erased-" + id + "@erased.invalid");
        person.setErased(true);
        person.setErasedAt(LocalDateTime.now(clock));
        Person saved = peopleRepository.saveAndFlush(person);
        // Re-check after the row is anonymised (still uncommitted) to narrow the window for an account opened concurrently.
        if (openAccountsVerifier.hasOpenAccounts(id)) {
            throw new PersonHasOpenAccountsException("Person with id = " + id + " opened an account during erasure; retry");
        }
        saveEvent(id, actor, ErasureAuditEvent.Outcome.COMPLETED, "Person PII anonymised; financial records retained", sourceAddress);
        return saved;
    }

    @Override
    public ErasureAuditEvent recordErasureEvent(UUID personId, String actor, ErasureAuditEvent.Outcome outcome, String detail, String sourceAddress) {
        if (outcome == ErasureAuditEvent.Outcome.COMPLETED) {
            throw new InvalidErasureAuditEventException("COMPLETED events are only recorded by the erasure itself");
        }
        return saveEvent(personId, actor, outcome, detail, sourceAddress);
    }

    private ErasureAuditEvent saveEvent(UUID personId, String actor, ErasureAuditEvent.Outcome outcome, String detail, String sourceAddress) {
        String safeActor = (actor == null || actor.trim().isEmpty()) ? UNKNOWN_ACTOR : actor.trim();
        return erasureAuditEventRepository.save(
                new ErasureAuditEvent(personId, safeActor, outcome, detail, sourceAddress, LocalDateTime.now(clock)));
    }

    @Override
    public List<ErasureAuditEvent> getErasureEvents(UUID personId) {
        return erasureAuditEventRepository.findAllByPersonIdOrderByOccurredAtAsc(personId);
    }

}
