package ru.kondrashov.personservice.services;

import org.springframework.stereotype.Service;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;
import ru.kondrashov.personservice.entities.Person;

import java.util.List;
import java.util.UUID;

public interface PeopleService {

    List<Person> getAll();
    Person get(UUID id);
    void save(Person person);
    void update(UUID id, Person person);
    Person erase(UUID id, String actor);

    ErasureAuditEvent recordErasureEvent(UUID personId, String actor, ErasureAuditEvent.Outcome outcome, String detail);

    List<ErasureAuditEvent> getErasureEvents(UUID personId);
}
