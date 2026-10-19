package ru.kondrashov.personservice.services;

import org.springframework.data.domain.Pageable;
import ru.kondrashov.personservice.entities.Person;

import java.util.List;
import java.util.UUID;

public interface PeopleService {

    List<Person> getAll(Pageable pageable);
    Person get(UUID id);
    void save(Person person);
    void update(UUID id, Person person);
    void delete(UUID id);
}
