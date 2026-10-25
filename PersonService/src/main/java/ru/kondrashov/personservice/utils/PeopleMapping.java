package ru.kondrashov.personservice.utils;

import org.springframework.stereotype.Component;
import ru.kondrashov.personservice.controllers.dto.PersonInputDTO;
import ru.kondrashov.personservice.controllers.dto.PersonViewDTO;
import ru.kondrashov.personservice.entities.Person;

public interface PeopleMapping {

    PersonViewDTO mapToPersonViewDTO(Person person);

    Person mapToPerson(PersonInputDTO personInputDTO);

}
