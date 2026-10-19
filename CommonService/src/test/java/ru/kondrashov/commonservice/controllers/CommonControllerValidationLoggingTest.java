package ru.kondrashov.commonservice.controllers;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import ru.kondrashov.commonservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.commonservice.services.CommonService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CommonControllerValidationLoggingTest {

    private static final String EMAIL = "jane.doe@example.com";
    private static final String PHONE = "+7 999 123-45-67";
    private static final String FIRST_NAME = "Jane";
    private static final String BIRTHDATE = "1990-13-45";

    private CommonService commonService;
    private Logger logger;
    private CommonController controller;

    @BeforeEach
    void setUp() {
        commonService = mock(CommonService.class);
        logger = mock(Logger.class);
        controller = new CommonController(commonService, logger);
    }

    @Test
    void createPersonLogsFieldAndCodeButNotRejectedValues() {
        PersonResponseDTO person = new PersonResponseDTO();
        BindingResult bindingResult = personErrors(person);

        assertThat(controller.createPerson(person, bindingResult)).isEqualTo("people/new");

        List<String> messages = loggedErrors(4);
        assertThat(messages).containsExactly(
                "Validation failed for person form: field 'email' violated Email",
                "Validation failed for person form: field 'phoneNumber' violated NotEmpty",
                "Validation failed for person form: field 'firstName' violated Size",
                "Validation failed for person form: field 'birthdate' violated typeMismatch");
        assertNoPii(messages);
        verifyNoInteractions(commonService);
    }

    @Test
    void updatePersonLogsFieldAndCodeButNotRejectedValues() {
        PersonResponseDTO person = new PersonResponseDTO();
        BindingResult bindingResult = personErrors(person);

        assertThat(controller.updatePerson(UUID.randomUUID(), person, bindingResult)).isEqualTo("people/edit");

        assertNoPii(loggedErrors(4));
        verifyNoInteractions(commonService);
    }

    @Test
    void accountAndBillFormsDoNotLogRejectedValues() {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        AccountResponseDTO account = new AccountResponseDTO();
        BindingResult accountErrors = new BeanPropertyBindingResult(account, "account");
        accountErrors.addError(new FieldError("account", "name", FIRST_NAME + "'s savings", false,
                new String[]{"NotEmpty"}, null, "must not be empty"));
        assertThat(controller.createAccount(account, accountErrors, personId)).isEqualTo("accounts/new");
        assertThat(controller.updateAccount(personId, accountId, account, accountErrors)).isEqualTo("accounts/edit");

        BillResponseDTO bill = new BillResponseDTO();
        BindingResult billErrors = new BeanPropertyBindingResult(bill, "bill");
        billErrors.addError(new FieldError("bill", "amount", EMAIL, false,
                new String[]{"typeMismatch"}, null, "invalid amount"));
        assertThat(controller.createBill(bill, billErrors, personId, accountId)).isEqualTo("/bills/new");
        assertThat(controller.updateBill(personId, accountId, UUID.randomUUID(), bill, billErrors)).isEqualTo("bills/edit");

        List<String> messages = loggedErrors(4);
        assertThat(messages).containsExactly(
                "Validation failed for account form: field 'name' violated NotEmpty",
                "Validation failed for account form: field 'name' violated NotEmpty",
                "Validation failed for bill form: field 'amount' violated typeMismatch",
                "Validation failed for bill form: field 'amount' violated typeMismatch");
        assertNoPii(messages);
        verifyNoInteractions(commonService);
    }

    private BindingResult personErrors(PersonResponseDTO person) {
        person.setEmail(EMAIL);
        person.setPhoneNumber(PHONE);
        person.setFirstName(FIRST_NAME);
        person.setBirthdate(LocalDate.of(1990, 1, 1));
        BindingResult bindingResult = new BeanPropertyBindingResult(person, "person");
        bindingResult.addError(new FieldError("person", "email", EMAIL, false,
                new String[]{"Email"}, null, "Email should be valid"));
        bindingResult.addError(new FieldError("person", "phoneNumber", PHONE, false,
                new String[]{"NotEmpty"}, null, "Phone number should not be empty"));
        bindingResult.addError(new FieldError("person", "firstName", FIRST_NAME, false,
                new String[]{"Size"}, null, "size must be between 1 and 30"));
        bindingResult.addError(new FieldError("person", "birthdate", BIRTHDATE, true,
                new String[]{"typeMismatch"}, null, "Failed to convert " + BIRTHDATE));
        return bindingResult;
    }

    private List<String> loggedErrors(int expected) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(logger, times(expected)).error(captor.capture());
        verifyNoMoreInteractions(logger);
        return captor.getAllValues();
    }

    private static void assertNoPii(List<String> messages) {
        for (String message : messages) {
            assertThat(message)
                    .doesNotContain(EMAIL)
                    .doesNotContain(PHONE)
                    .doesNotContain(FIRST_NAME)
                    .doesNotContain(BIRTHDATE)
                    .doesNotContain("rejected value");
        }
    }
}
