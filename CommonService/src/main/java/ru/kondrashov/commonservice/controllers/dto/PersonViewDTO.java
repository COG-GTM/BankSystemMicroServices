package ru.kondrashov.commonservice.controllers.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Person as returned by PersonService GET /v1/people(/{id}); rendered by the people templates.
 */
@NoArgsConstructor
@Getter
@Setter
public class PersonViewDTO {

    private UUID id;

    private String firstName;

    private String lastName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthdate;

    private String phoneNumber;

    private String email;

}
