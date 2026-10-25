package ru.kondrashov.personservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PersonResponseDTO {

    private UUID id;

    private String firstName;

    private String lastName;

    private LocalDate birthdate;

    private String phoneNumber;

    private String email;


}
