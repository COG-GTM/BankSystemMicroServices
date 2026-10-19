package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.personservice.audit.PersonAuditService;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonNotFoundException;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.util.List;
import java.util.UUID;

@Service
public class PeopleServiceImpl implements PeopleService{
    private final PeopleRepository peopleRepository;
    private final PersonAuditService personAuditService;

    @Autowired
    public PeopleServiceImpl(PeopleRepository peopleRepository, PersonAuditService personAuditService) {
        this.peopleRepository = peopleRepository;
        this.personAuditService = personAuditService;
    }

    public List<Person> getAll(){
        return peopleRepository.findAll();
    }

    public Person get(UUID id){
        return peopleRepository.
                getPersonById(id).
                orElseThrow( () -> new PersonNotFoundException("Person with id = "+id+" not found"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(Person person){
        Person saved = peopleRepository.save(person);
        personAuditService.recordCreate(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, Person changedPerson) {
        Person originPerson = get(id);
        Person before = new Person(originPerson.getId(), originPerson.getFirstName(), originPerson.getLastName(),
                originPerson.getBirthdate(), originPerson.getPhoneNumber(), originPerson.getEmail());
        originPerson.setFirstName(changedPerson.getFirstName());
        originPerson.setLastName(changedPerson.getLastName());
        originPerson.setBirthdate(changedPerson.getBirthdate());
        originPerson.setPhoneNumber(changedPerson.getPhoneNumber());
        originPerson.setEmail(changedPerson.getEmail());

        peopleRepository.save(originPerson);
        personAuditService.recordUpdate(before, originPerson);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id){
        Person person = get(id);
        peopleRepository.delete(person);
        personAuditService.recordDelete(person);
    }

}
