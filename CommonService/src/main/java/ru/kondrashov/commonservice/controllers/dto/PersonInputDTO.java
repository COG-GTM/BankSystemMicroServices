package ru.kondrashov.commonservice.controllers.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Person create/edit form body, forwarded as-is to PersonService POST/PUT /v1/people.
 * Mirrors PersonService's PersonInputDTO constraints so the form can show errors before the call.
 */
@NoArgsConstructor
@Getter
@Setter
public class PersonInputDTO {

    @NotEmpty(message = "First name should not be empty")
    @Size(min = 1, max = 30)
    private String firstName;

    @NotEmpty(message = "Last name should not be empty")
    @Size(min = 1, max = 30)
    private String lastName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "Birthdate should not be empty")
    private LocalDate birthdate;

    @NotEmpty(message = "Phone number should not be empty")
    private String phoneNumber;

    @NotEmpty(message = "Email should not be empty")
    @Email(message = "Email should be valid")
    private String email;
}
