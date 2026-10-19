package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.kondrashov.personservice.audit.AuditOperation;
import ru.kondrashov.personservice.audit.PersonAuditService;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonNotFoundException;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PeopleServiceImpl implements PeopleService{

    static final List<String> PERSONAL_DATA_FIELDS = Collections.unmodifiableList(
            Arrays.asList("firstName", "lastName", "birthdate", "phoneNumber", "email"));

    private final PeopleRepository peopleRepository;
    private final PersonAuditService auditService;

    @Autowired
    public PeopleServiceImpl(PeopleRepository peopleRepository, PersonAuditService auditService) {
        this.peopleRepository = peopleRepository;
        this.auditService = auditService;
    }

    public List<Person> getAll(){
        return peopleRepository.findAll();
    }

    public Person get(UUID id){
        return peopleRepository.
                getPersonById(id).
                orElseThrow( () -> new PersonNotFoundException("Person with id = "+id+" not found"));
    }

    public void save(Person person){
        List<String> providedFields = providedFields(person);
        Person saved;
        try {
            saved = peopleRepository.save(person);
        } catch (RuntimeException e) {
            auditService.recordFailure(AuditOperation.CREATE, person.getId(), providedFields, e);
            throw e;
        }
        auditService.recordSuccess(AuditOperation.CREATE, saved.getId(), providedFields);
    }

    public void update(UUID id, Person changedPerson) {
        List<String> changedFields = Collections.emptyList();
        try {
            Person originPerson = get(id);
            changedFields = changedFields(originPerson, changedPerson);
            originPerson.setFirstName(changedPerson.getFirstName());
            originPerson.setLastName(changedPerson.getLastName());
            originPerson.setBirthdate(changedPerson.getBirthdate());
            originPerson.setPhoneNumber(changedPerson.getPhoneNumber());
            originPerson.setEmail(changedPerson.getEmail());

            peopleRepository.save(originPerson);
        } catch (RuntimeException e) {
            auditService.recordFailure(AuditOperation.UPDATE, id, changedFields, e);
            throw e;
        }
        auditService.recordSuccess(AuditOperation.UPDATE, id, changedFields);
    }

    public void delete(UUID id){
        try {
            peopleRepository.delete(get(id));
        } catch (RuntimeException e) {
            auditService.recordFailure(AuditOperation.DELETE, id, PERSONAL_DATA_FIELDS, e);
            throw e;
        }
        auditService.recordSuccess(AuditOperation.DELETE, id, PERSONAL_DATA_FIELDS);
    }

    static List<String> providedFields(Person person) {
        List<String> fields = new ArrayList<>();
        addIf(fields, "firstName", person.getFirstName() != null);
        addIf(fields, "lastName", person.getLastName() != null);
        addIf(fields, "birthdate", person.getBirthdate() != null);
        addIf(fields, "phoneNumber", person.getPhoneNumber() != null);
        addIf(fields, "email", person.getEmail() != null);
        return fields;
    }

    static List<String> changedFields(Person origin, Person changed) {
        List<String> fields = new ArrayList<>();
        addIf(fields, "firstName", !Objects.equals(origin.getFirstName(), changed.getFirstName()));
        addIf(fields, "lastName", !Objects.equals(origin.getLastName(), changed.getLastName()));
        addIf(fields, "birthdate", !Objects.equals(origin.getBirthdate(), changed.getBirthdate()));
        addIf(fields, "phoneNumber", !Objects.equals(origin.getPhoneNumber(), changed.getPhoneNumber()));
        addIf(fields, "email", !Objects.equals(origin.getEmail(), changed.getEmail()));
        return fields;
    }

    private static void addIf(List<String> fields, String name, boolean condition) {
        if (condition) {
            fields.add(name);
        }
    }

}
