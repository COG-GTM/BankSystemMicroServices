package ru.kondrashov.commonservice.controllers.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;
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
    void emptyFormInputReportsEveryField() {
        Set<String> fields = validator.validate(new PersonInputDTO()).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder("firstName", "lastName", "birthdate", "phoneNumber", "email");
    }

    @Test
    void viewDtoHasNoConstraintsAndCanBeValidated() {
        PersonViewDTO view = new PersonViewDTO();
        view.setId(UUID.randomUUID());

        assertThat(validator.getConstraintsForClass(PersonViewDTO.class).isBeanConstrained()).isFalse();
        assertThat(validator.validate(view)).isEmpty();
    }
}
