package ru.kondrashov.personservice.utils;

import org.junit.jupiter.api.Test;
import ru.kondrashov.personservice.controllers.dto.PersonInputDTO;
import ru.kondrashov.personservice.controllers.dto.PersonViewDTO;
import ru.kondrashov.personservice.entities.Person;


import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PeopleMappingImplTest {

    private PeopleMappingImpl peopleMapping = new PeopleMappingImpl();

    @Test
    void mapToPerson() {
        PersonInputDTO personInputDTO = new PersonInputDTO(
                "Bill",
                "Johnson",
                LocalDate.of(2000,12,26),
                "+7 123 258 54 85",
                "BillJohnson@mail.com"
        );

        Person person = peopleMapping.mapToPerson(personInputDTO);

        assertEquals(person.getFirstName(),personInputDTO.getFirstName());
        assertEquals(person.getLastName(),personInputDTO.getLastName());
        assertEquals(person.getBirthdate(),personInputDTO.getBirthdate());
        assertEquals(person.getEmail(),personInputDTO.getEmail());
        assertEquals(person.getPhoneNumber(),personInputDTO.getPhoneNumber());
     }

    @Test
    void mapToPersonViewDTO() {

        Person person = new Person(
                UUID.randomUUID(),
                "Bill",
                "Johnson",
                LocalDate.of(2000,12,26),
                "+7 123 258 54 85",
                "BillJohnson@mail.com"
        );

        PersonViewDTO personViewDTO = peopleMapping.mapToPersonViewDTO(person);

        assertEquals(person.getFirstName(),     personViewDTO.getFirstName());
        assertEquals(person.getLastName(),      personViewDTO.getLastName());
        assertEquals(person.getBirthdate(),     personViewDTO.getBirthdate());
        assertEquals(person.getEmail(),         personViewDTO.getEmail());
        assertEquals(person.getPhoneNumber(),   personViewDTO.getPhoneNumber());
    }
}