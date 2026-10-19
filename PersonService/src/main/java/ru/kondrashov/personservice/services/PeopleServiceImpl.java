package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonErasedException;
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
    private final Clock clock;

    @Autowired
    public PeopleServiceImpl(PeopleRepository peopleRepository, ErasureAuditEventRepository erasureAuditEventRepository) {
        this(peopleRepository, erasureAuditEventRepository, Clock.systemUTC());
    }

    PeopleServiceImpl(PeopleRepository peopleRepository, ErasureAuditEventRepository erasureAuditEventRepository, Clock clock) {
        this.peopleRepository = peopleRepository;
        this.erasureAuditEventRepository = erasureAuditEventRepository;
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
     * The COMPLETED audit event is written in the same local transaction as the anonymisation.
     * Idempotent: erasing an already erased person is a no-op.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Person erase(UUID id, String actor){
        Person person = get(id);
        if (person.isErased()) {
            return person;
        }
        person.setFirstName(ERASED);
        person.setLastName(ERASED);
        person.setBirthdate(null);
        person.setPhoneNumber(ERASED);
        person.setEmail("erased-" + id + "@erased.invalid");
        person.setErased(true);
        person.setErasedAt(LocalDateTime.now(clock));
        Person saved = peopleRepository.save(person);
        recordErasureEvent(id, actor, ErasureAuditEvent.Outcome.COMPLETED, "Person PII anonymised; financial records retained");
        return saved;
    }

    @Override
    public ErasureAuditEvent recordErasureEvent(UUID personId, String actor, ErasureAuditEvent.Outcome outcome, String detail) {
        String safeActor = (actor == null || actor.trim().isEmpty()) ? UNKNOWN_ACTOR : actor.trim();
        return erasureAuditEventRepository.save(
                new ErasureAuditEvent(personId, safeActor, outcome, detail, LocalDateTime.now(clock)));
    }

    @Override
    public List<ErasureAuditEvent> getErasureEvents(UUID personId) {
        return erasureAuditEventRepository.findAllByPersonIdOrderByOccurredAtAsc(personId);
    }

}
