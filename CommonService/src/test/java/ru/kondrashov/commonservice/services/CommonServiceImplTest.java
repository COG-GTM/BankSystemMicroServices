package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.AccountResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.PersonResponseDTO;

import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServiceImplTest {

    private static final String PEOPLE = "http://person/v1/people";
    private static final String ACCOUNTS = "http://account/v1/people/%s/accounts";

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    private MockRestServiceServer server;
    private CommonServiceImpl service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new CommonServiceImpl(restTemplate, LogManager.getLogger(CommonServiceImplTest.class));
        ReflectionTestUtils.setField(service, "peopleUrl", PEOPLE);
        ReflectionTestUtils.setField(service, "personUrl", PEOPLE + "/%s");
        ReflectionTestUtils.setField(service, "accountsUrl", ACCOUNTS);
        ReflectionTestUtils.setField(service, "accountUrl", ACCOUNTS + "/%s");
        ReflectionTestUtils.setField(service, "billsUrl", ACCOUNTS + "/%s/bills");
        ReflectionTestUtils.setField(service, "billUrl", ACCOUNTS + "/%s/bills/%s");
        ReflectionTestUtils.setField(service, "transactionsUrl", ACCOUNTS + "/%s/bills/%s/transactions");
    }

    @Test
    void getPersonByIdFormatsUrlAndReturnsBody() {
        server.expect(requestTo(PEOPLE + "/" + personId)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + personId + "\",\"firstName\":\"Ann\"}", MediaType.APPLICATION_JSON));

        PersonRequestDTO person = service.getPersonById(personId);

        assertThat(person.getId()).isEqualTo(personId);
        assertThat(person.getFirstName()).isEqualTo("Ann");
        server.verify();
    }

    @Test
    void getFinancialTransactionsUsesAllThreeIds() {
        server.expect(requestTo(accountUrl() + "/bills/" + billId + "/transactions")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{},{}]", MediaType.APPLICATION_JSON));

        assertThat(service.getFinancialTransactionsByBillId(personId, accountId, billId)).hasSize(2);
        server.verify();
    }

    @Test
    void createPersonPostsBodyAndAcceptsCreated() throws Exception {
        PersonResponseDTO person = new PersonResponseDTO();
        person.setFirstName("Ann");
        server.expect(requestTo(PEOPLE)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.firstName").value("Ann"))
                .andRespond(withStatus(HttpStatus.CREATED));

        service.createPerson(person);
        server.verify();
    }

    @Test
    void createRejectsNonCreatedSuccessStatus() {
        server.expect(requestTo(String.format(ACCOUNTS, personId))).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> service.createAccount(personId, new AccountResponseDTO()))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("200");
        server.verify();
    }

    @Test
    void updatesSendBodyWithoutIdHeader() {
        server.expect(requestTo(PEOPLE + "/" + personId)).andExpect(method(HttpMethod.PUT))
                .andExpect(noHeader("id")).andRespond(withSuccess());
        server.expect(requestTo(accountUrl())).andExpect(method(HttpMethod.PUT))
                .andExpect(noHeader("id")).andRespond(withSuccess());
        server.expect(requestTo(billUrl())).andExpect(method(HttpMethod.PUT))
                .andExpect(noHeader("id")).andExpect(jsonPath("$.currency").value("EUR"))
                .andRespond(withSuccess());

        BillResponseDTO bill = new BillResponseDTO();
        bill.setCurrency(Currency.getInstance("EUR"));
        service.updatePerson(personId, new PersonResponseDTO());
        service.updateAccount(personId, accountId, new AccountResponseDTO());
        service.updateBill(personId, accountId, billId, bill);
        server.verify();
    }

    @Test
    void deleteBillTargetsBillUrl() {
        server.expect(requestTo(billUrl())).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        service.deleteBill(personId, accountId, billId);
        server.verify();
    }

    @Test
    void deletePersonDeletesAccountsThenPerson() {
        server.expect(requestTo(String.format(ACCOUNTS, personId))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + accountId + "\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(accountUrl())).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());
        server.expect(requestTo(PEOPLE + "/" + personId)).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());

        service.deletePerson(personId);
        server.verify();
    }

    private String accountUrl() {
        return String.format(ACCOUNTS, personId) + "/" + accountId;
    }

    private String billUrl() {
        return accountUrl() + "/bills/" + billId;
    }

    private static RequestMatcher noHeader(String name) {
        return request -> assertThat(request.getHeaders().containsKey(name)).as("header %s", name).isFalse();
    }
}
