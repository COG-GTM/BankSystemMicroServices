package ru.kondrashov.personservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound body of GET /v1/people and GET /v1/people/{id}.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PersonViewDTO {

    private UUID id;

    private String firstName;

    private String lastName;

    private LocalDate birthdate;

    private String phoneNumber;

    private String email;

}
