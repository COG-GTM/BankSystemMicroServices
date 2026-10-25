package ru.kondrashov.personservice.controllers.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.kondrashov.personservice.entities.Person;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PersonDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validInputHasNoViolations() {
        PersonInputDTO input = new PersonInputDTO("Bill", "Johnson", LocalDate.of(2000, 12, 26),
                "+7 123 258 54 85", "BillJohnson@mail.com");

        assertThat(validator.validate(input)).isEmpty();
    }

    @Test
    void emptyInputReportsEveryField() {
        Set<String> fields = validator.validate(new PersonInputDTO()).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("firstName", "lastName", "birthdate", "phoneNumber", "email");
    }

    @Test
    void inputEnforcesNameLengthAndEmailFormat() {
        PersonInputDTO input = new PersonInputDTO(repeat('a', 31), repeat('b', 30), LocalDate.of(2000, 12, 26),
                "+7 123 258 54 85", "not-an-email");

        Set<ConstraintViolation<PersonInputDTO>> violations = validator.validate(input);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("firstName", "email");
        assertThat(violations).extracting(ConstraintViolation::getMessage).contains("Email should be valid");
    }

    @Test
    void onlyTheInputDtoCarriesConstraints() {
        assertThat(validator.getConstraintsForClass(PersonInputDTO.class).isBeanConstrained()).isTrue();
        assertThat(validator.getConstraintsForClass(PersonViewDTO.class).isBeanConstrained()).isFalse();
        assertThat(validator.getConstraintsForClass(Person.class).isBeanConstrained()).isFalse();
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
