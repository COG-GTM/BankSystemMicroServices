package ru.kondrashov.personservice.utils;

import org.springframework.stereotype.Component;
import ru.kondrashov.personservice.controllers.dto.PersonInputDTO;
import ru.kondrashov.personservice.controllers.dto.PersonViewDTO;
import ru.kondrashov.personservice.entities.Person;

@Component
public class PeopleMappingImpl implements PeopleMapping{

    @Override
    public Person mapToPerson(PersonInputDTO personInputDTO){
        Person person = new Person();
        person.setFirstName(personInputDTO.getFirstName());
        person.setLastName(personInputDTO.getLastName());
        person.setBirthdate(personInputDTO.getBirthdate());
        person.setPhoneNumber(personInputDTO.getPhoneNumber());
        person.setEmail(personInputDTO.getEmail());
        return person;
    }

    @Override
    public PersonViewDTO mapToPersonViewDTO(Person person){
        PersonViewDTO personViewDTO = new PersonViewDTO();
        personViewDTO.setId(            person.getId());
        personViewDTO.setFirstName(     person.getFirstName());
        personViewDTO.setLastName(      person.getLastName());
        personViewDTO.setBirthdate(     person.getBirthdate());
        personViewDTO.setPhoneNumber(   person.getPhoneNumber());
        personViewDTO.setEmail(         person.getEmail());
        return personViewDTO;
    }

}
