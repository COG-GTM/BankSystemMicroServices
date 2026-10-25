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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServiceImplTest {

    private static final String PEOPLE_URL = "http://people/v1/people";
    private static final String PERSON_URL = "http://people/v1/people/%s";
    private static final String ACCOUNTS_URL = "http://accounts/v1/people/%s/accounts";
    private static final String ACCOUNT_URL = "http://accounts/v1/people/%s/accounts/%s";
    private static final String BILLS_URL = "http://accounts/v1/people/%s/accounts/%s/bills";
    private static final String BILL_URL = "http://accounts/v1/people/%s/accounts/%s/bills/%s";

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    private MockRestServiceServer server;
    private CommonServiceImpl service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new CommonServiceImpl(restTemplate, LogManager.getLogger(CommonServiceImplTest.class));
        ReflectionTestUtils.setField(service, "peopleUrl", PEOPLE_URL);
        ReflectionTestUtils.setField(service, "personUrl", PERSON_URL);
        ReflectionTestUtils.setField(service, "accountsUrl", ACCOUNTS_URL);
        ReflectionTestUtils.setField(service, "accountUrl", ACCOUNT_URL);
        ReflectionTestUtils.setField(service, "billsUrl", BILLS_URL);
        ReflectionTestUtils.setField(service, "billUrl", BILL_URL);
    }

    @Test
    void createMethodsDeclareNoCheckedExceptions() throws NoSuchMethodException {
        Method[] creates = {
                CommonService.class.getMethod("createPerson", PersonResponseDTO.class),
                CommonService.class.getMethod("createAccount", UUID.class, AccountResponseDTO.class),
                CommonService.class.getMethod("createBill", UUID.class, UUID.class, BillResponseDTO.class)
        };
        for (Method create : creates) {
            assertThat(create.getExceptionTypes()).as(create.getName()).isEmpty();
        }
    }

    @Test
    void createPersonSucceedsOnCreated() {
        server.expect(requestTo(PEOPLE_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));

        service.createPerson(new PersonResponseDTO());

        server.verify();
    }

    @Test
    void createPersonTranslatesClientErrorWithStatusAndBody() {
        server.expect(requestTo(PEOPLE_URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"email already used\"}"));

        assertThatThrownBy(() -> service.createPerson(new PersonResponseDTO()))
                .isInstanceOfSatisfying(DownstreamServiceException.class, e -> {
                    assertThat(e.getMethod()).isEqualTo(HttpMethod.POST);
                    assertThat(e.getUrl()).isEqualTo(PEOPLE_URL);
                    assertThat(e.getStatusCode()).isEqualTo(400);
                    assertThat(e.getResponseBody()).contains("email already used");
                    assertThat(e.getStatusDescription()).isEqualTo("400 Bad Request");
                    assertThat(e.getMessage()).contains("POST " + PEOPLE_URL, "status 400", "email already used");
                });
    }

    @Test
    void createPersonRejectsNonCreatedSuccessStatus() {
        server.expect(requestTo(PEOPLE_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.createPerson(new PersonResponseDTO()))
                .isInstanceOfSatisfying(DownstreamServiceException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(200));
    }

    @Test
    void createAccountTranslatesServerError() {
        String url = String.format(ACCOUNTS_URL, personId);
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> service.createAccount(personId, new AccountResponseDTO()))
                .isInstanceOfSatisfying(DownstreamServiceException.class, e -> {
                    assertThat(e.getUrl()).isEqualTo(url);
                    assertThat(e.getStatusCode()).isEqualTo(500);
                });
    }

    @Test
    void createBillTranslatesIoFailureWithoutStatus() {
        server.expect(requestTo(String.format(BILLS_URL, personId, accountId)))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> service.createBill(personId, accountId, new BillResponseDTO()))
                .isInstanceOfSatisfying(DownstreamServiceException.class, e -> {
                    assertThat(e.getStatusCode()).isNull();
                    assertThat(e.getStatusDescription()).isEqualTo("no response");
                    assertThat(e.getMessage()).contains("Connection refused");
                });
    }

    @Test
    void updateTranslatesDownstreamErrorLikeCreate() {
        server.expect(requestTo(String.format(PERSON_URL, personId))).andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.updatePerson(personId, new PersonResponseDTO()))
                .isInstanceOfSatisfying(DownstreamServiceException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(404));
    }

    @Test
    void deleteTranslatesDownstreamErrorLikeCreate() {
        server.expect(requestTo(String.format(BILL_URL, personId, accountId, billId))).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.deleteBill(personId, accountId, billId))
                .isInstanceOfSatisfying(DownstreamServiceException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(409));
    }

    @Test
    void updateAndDeleteSucceedOnSuccessStatus() {
        server.expect(requestTo(String.format(ACCOUNT_URL, personId, accountId))).andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("{}"));
        server.expect(requestTo(String.format(ACCOUNT_URL, personId, accountId))).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.updateAccount(personId, accountId, new AccountResponseDTO());
        service.deleteAccount(personId, accountId);

        server.verify();
    }
}
