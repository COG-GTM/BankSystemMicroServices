package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.ResponseCreator;
import ru.kondrashov.commonservice.exceptions.PersonDeletionException;

import java.io.IOException;
import java.net.ConnectException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CommonServiceImplTest {

    private static final String PERSON_URL = "http://person-service/v1/people/%s";
    private static final String ACCOUNTS_URL = "http://account-service/v1/people/%s/accounts";

    private final UUID personId = UUID.randomUUID();
    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getLogger(CommonServiceImplTest.class));
        ReflectionTestUtils.setField(commonService, "personUrl", PERSON_URL);
        ReflectionTestUtils.setField(commonService, "accountsUrl", ACCOUNTS_URL);
    }

    @Test
    void deletesAccountsInOneCallThenPerson() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(ExpectedCount.once(), requestTo(String.format(PERSON_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        commonService.deletePerson(personId);

        server.verify();
    }

    @Test
    void accountStepFailureKeepsPersonAndReportsNothingDeleted() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        PersonDeletionException exp = catchThrowableOfType(
                () -> commonService.deletePerson(personId), PersonDeletionException.class);

        server.verify();
        assertThat(exp).isNotNull();
        assertThat(exp.getPersonId()).isEqualTo(personId);
        assertThat(exp.getFailedStep()).isEqualTo(PersonDeletionException.Step.DELETE_ACCOUNTS);
        assertThat(exp.isAccountsDeleted()).isFalse();
        assertThat(exp.isOutcomeKnown()).isTrue();
        assertThat(exp.getMessage()).contains("nothing was deleted");
    }

    @Test
    void personStepFailureReportsAccountsAlreadyDeleted() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(ExpectedCount.once(), requestTo(String.format(PERSON_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        PersonDeletionException exp = catchThrowableOfType(
                () -> commonService.deletePerson(personId), PersonDeletionException.class);

        server.verify();
        assertThat(exp).isNotNull();
        assertThat(exp.getFailedStep()).isEqualTo(PersonDeletionException.Step.DELETE_PERSON);
        assertThat(exp.isAccountsDeleted()).isTrue();
        assertThat(exp.isOutcomeKnown()).isTrue();
        assertThat(exp.getMessage()).contains("accounts were already deleted", "person record still exists");
    }

    @Test
    void accountStepWithoutResponseReportsUnknownOutcome() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(connectionLost());

        PersonDeletionException exp = catchThrowableOfType(
                () -> commonService.deletePerson(personId), PersonDeletionException.class);

        server.verify();
        assertThat(exp).isNotNull();
        assertThat(exp.getFailedStep()).isEqualTo(PersonDeletionException.Step.DELETE_ACCOUNTS);
        assertThat(exp.isAccountsDeleted()).isFalse();
        assertThat(exp.isOutcomeKnown()).isFalse();
        assertThat(exp.getMessage()).contains("may or may not have been deleted").doesNotContain("nothing was deleted");
    }

    @Test
    void personStepWithoutResponseReportsUnknownPersonState() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(ExpectedCount.once(), requestTo(String.format(PERSON_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(connectionLost());

        PersonDeletionException exp = catchThrowableOfType(
                () -> commonService.deletePerson(personId), PersonDeletionException.class);

        server.verify();
        assertThat(exp).isNotNull();
        assertThat(exp.getFailedStep()).isEqualTo(PersonDeletionException.Step.DELETE_PERSON);
        assertThat(exp.isAccountsDeleted()).isTrue();
        assertThat(exp.isOutcomeKnown()).isFalse();
        assertThat(exp.getMessage()).contains("may or may not have been deleted").doesNotContain("still exists");
    }

    @Test
    void accountServiceUnreachableReportsNothingDeleted() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(request -> {
                    throw new ConnectException("Connection refused");
                });

        PersonDeletionException exp = catchThrowableOfType(
                () -> commonService.deletePerson(personId), PersonDeletionException.class);

        server.verify();
        assertThat(exp).isNotNull();
        assertThat(exp.getFailedStep()).isEqualTo(PersonDeletionException.Step.DELETE_ACCOUNTS);
        assertThat(exp.isOutcomeKnown()).isTrue();
        assertThat(exp.getMessage()).contains("nothing was deleted");
    }

    @Test
    void absentPersonCountsAsDeleted() {
        server.expect(ExpectedCount.once(), requestTo(String.format(ACCOUNTS_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(ExpectedCount.once(), requestTo(String.format(PERSON_URL, personId)))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        commonService.deletePerson(personId);

        server.verify();
    }

    private static ResponseCreator connectionLost() {
        return request -> {
            throw new IOException("Connection reset");
        };
    }
}
