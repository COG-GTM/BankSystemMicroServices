package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;
import ru.kondrashov.commonservice.exceptions.DownstreamServiceException;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class CommonServiceImplTest {

    private static final UUID PERSON_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BILL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private MockRestServiceServer server;
    private CommonServiceImpl service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new CommonServiceImpl(restTemplate, LogManager.getLogger(CommonServiceImplTest.class));
        ReflectionTestUtils.setField(service, "peopleUrl", "http://people/v1/people");
        ReflectionTestUtils.setField(service, "accountsUrl", "http://accounts/v1/people/%s/accounts");
        ReflectionTestUtils.setField(service, "billsUrl", "http://accounts/v1/people/%s/accounts/%s/bills");
        ReflectionTestUtils.setField(service, "billUrl", "http://accounts/v1/people/%s/accounts/%s/bills/%s");
    }

    @Test
    void createBillErrorResponseCarriesStatusAndBody() {
        String url = "http://accounts/v1/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "/bills";
        String body = "{\"message\":\"Account with id " + ACCOUNT_ID + " not found\",\"statusCode\":404}";
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(body));

        DownstreamServiceException e = catchThrowableOfType(
                () -> service.createBill(PERSON_ID, ACCOUNT_ID, new BillResponseDTO()), DownstreamServiceException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(e.getUrl()).isEqualTo(url);
        assertThat(e.getResponseBody()).isEqualTo(body);
        assertThat(e.getMessage()).isEqualTo("POST " + url + " failed: 404 Not Found body=" + body);
        assertThat(e.getUserMessage()).isEqualTo("the downstream service responded 404 Not Found: Account with id " + ACCOUNT_ID + " not found");
        server.verify();
    }

    @Test
    void createPersonUnexpectedSuccessStatusIsRejected() {
        server.expect(requestTo("http://people/v1/people"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{}"));

        DownstreamServiceException e = catchThrowableOfType(
                () -> service.createPerson(new PersonResponseDTO()), DownstreamServiceException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(e.getUserMessage()).isEqualTo("the downstream service responded 200 OK");
    }

    @Test
    void createAccountCreatedSucceeds() {
        server.expect(requestTo("http://accounts/v1/people/" + PERSON_ID + "/accounts"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatCode(() -> service.createAccount(PERSON_ID, new AccountResponseDTO())).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void unreachableServiceHasNoStatus() {
        server.expect(requestTo("http://people/v1/people")).andRespond(request -> {
            throw new IOException("Connection refused");
        });

        DownstreamServiceException e = catchThrowableOfType(
                () -> service.createPerson(new PersonResponseDTO()), DownstreamServiceException.class);

        assertThat(e.getStatus()).isNull();
        assertThat(e.getMessage()).startsWith("POST http://people/v1/people failed: I/O error").contains("Connection refused");
        assertThat(e.getUserMessage()).isEqualTo("the downstream service is unavailable");
    }

    @Test
    void deleteBillErrorIsWrappedLikeCreate() {
        String url = "http://accounts/v1/people/" + PERSON_ID + "/accounts/" + ACCOUNT_ID + "/bills/" + BILL_ID;
        server.expect(requestTo(url)).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        DownstreamServiceException e = catchThrowableOfType(
                () -> service.deleteBill(PERSON_ID, ACCOUNT_ID, BILL_ID), DownstreamServiceException.class);

        assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(e.getResponseBody()).isEqualTo("boom");
        assertThat(e.getUserMessage()).isEqualTo("the downstream service responded 500 Internal Server Error");
    }
}
