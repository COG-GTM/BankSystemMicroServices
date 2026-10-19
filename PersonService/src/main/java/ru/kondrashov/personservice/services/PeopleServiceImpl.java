package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonNotFoundException;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PeopleServiceImpl implements PeopleService{
    static final String ERASED = "ERASED";
    static final LocalDate ERASED_BIRTHDATE = LocalDate.of(1900, 1, 1);

    private final PeopleRepository peopleRepository;

    @Autowired
    public PeopleServiceImpl(PeopleRepository peopleRepository) {
        this.peopleRepository = peopleRepository;
    }

    public List<Person> getAll(){
        return peopleRepository.findAllByErasedFalse();
    }

    public Person get(UUID id){
        return peopleRepository.
                getPersonByIdAndErasedFalse(id).
                orElseThrow( () -> new PersonNotFoundException("Person with id = "+id+" not found"));
    }

    public void save(Person person){
        peopleRepository.save(person);
    }

    public void update(UUID id, Person changedPerson) {
        Person originPerson = get(id);
        originPerson.setFirstName(changedPerson.getFirstName());
        originPerson.setLastName(changedPerson.getLastName());
        originPerson.setBirthdate(changedPerson.getBirthdate());
        originPerson.setPhoneNumber(changedPerson.getPhoneNumber());
        originPerson.setEmail(changedPerson.getEmail());

        peopleRepository.save(originPerson);

    }

    /**
     * GDPR erasure: the record is kept as an anonymised tombstone so that retained
     * ledger data in AccountService still references a valid (but unidentifiable) owner.
     */
    public void delete(UUID id){
        Person person = get(id);
        person.setFirstName(ERASED);
        person.setLastName(ERASED);
        person.setBirthdate(ERASED_BIRTHDATE);
        person.setPhoneNumber(ERASED);
        person.setEmail("erased-" + id + "@erased.invalid");
        person.setErased(true);
        person.setErasedAt(LocalDateTime.now());
        peopleRepository.save(person);
    }

}
