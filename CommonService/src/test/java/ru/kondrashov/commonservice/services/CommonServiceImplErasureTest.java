package ru.kondrashov.commonservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CommonServiceImplErasureTest {

    private static final String ACTOR = "operator@bank";

    private final UUID personId = UUID.randomUUID();
    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();

    private MockRestServiceServer server;
    private CommonServiceImpl service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(false).build();
        service = new CommonServiceImpl(restTemplate, LogManager.getLogger(CommonServiceImplErasureTest.class));
        ReflectionTestUtils.setField(service, "personUrl", "http://person/v1/people/%s");
        ReflectionTestUtils.setField(service, "accountsUrl", "http://account/v1/people/%s/accounts");
        ReflectionTestUtils.setField(service, "billsUrl", "http://account/v1/people/%s/accounts/%s/bills");
        ReflectionTestUtils.setField(service, "accountClosureUrl", "http://account/v1/people/%s/accounts/%s/closure");
        ReflectionTestUtils.setField(service, "erasureAuditUrl", "http://person/v1/people/%s/erasure-audit-events");
    }

    @Test
    void erasesPersonAfterSoftClosingSettledAccounts() {
        expectAudit("STARTED");
        expectAccounts();
        expectBills(accountA, "0.00");
        expectBills(accountB, "0");
        expectClosure(accountA).andRespond(withNoContent());
        expectClosure(accountB).andRespond(withNoContent());
        server.expect(requestTo(personUrl())).andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Actor", ACTOR))
                .andRespond(withNoContent());

        service.deletePerson(personId, ACTOR);

        server.verify();
    }

    @Test
    void rejectsWithoutChangingAnythingWhenABalanceIsNonZero() {
        expectAudit("STARTED");
        expectAccounts();
        expectBills(accountA, "0");
        expectBills(accountB, "12.50");
        expectAudit("REJECTED");

        assertThatThrownBy(() -> service.deletePerson(personId, ACTOR))
                .isInstanceOf(PersonErasureException.class)
                .hasMessageContaining("Non-zero balance");

        // no closure or person DELETE expectations were registered: any such call would fail verify()
        server.verify();
    }

    @Test
    void recordsFailureAndCanBeResumedAfterAMidWorkflowError() {
        expectAudit("STARTED");
        expectAccounts();
        expectBills(accountA, "0");
        expectBills(accountB, "0");
        expectClosure(accountA).andRespond(withNoContent());
        expectClosure(accountB).andRespond(withServerError());
        expectAudit("FAILED");

        assertThatThrownBy(() -> service.deletePerson(personId, ACTOR))
                .isInstanceOf(PersonErasureException.class)
                .hasMessageContaining("can be retried");
        server.verify();
        server.reset();

        // retry: account A is already closed so AccountService no longer lists it
        expectAudit("STARTED");
        server.expect(requestTo(accountsUrl())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + accountB + "\"}]", MediaType.APPLICATION_JSON));
        expectBills(accountB, "0");
        expectClosure(accountB).andRespond(withNoContent());
        server.expect(requestTo(personUrl())).andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        service.deletePerson(personId, ACTOR);
        server.verify();
    }

    @Test
    void doesNothingDestructiveWhenAuditStoreIsUnavailable() {
        server.expect(ExpectedCount.once(), requestTo(auditUrl())).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(ExpectedCount.once(), requestTo(auditUrl())).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.deletePerson(personId, ACTOR))
                .isInstanceOf(RuntimeException.class);
    }

    private void expectAudit(String outcome) {
        server.expect(requestTo(auditUrl())).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.actor").value(ACTOR))
                .andExpect(jsonPath("$.outcome").value(outcome))
                .andRespond(withStatus(HttpStatus.CREATED));
    }

    private void expectAccounts() {
        server.expect(requestTo(accountsUrl())).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + accountA + "\"},{\"id\":\"" + accountB + "\"}]", MediaType.APPLICATION_JSON));
    }

    private void expectBills(UUID accountId, String amount) {
        server.expect(requestTo(accountsUrl() + "/" + accountId + "/bills")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + UUID.randomUUID() + "\",\"amount\":" + amount + "}]", MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.client.ResponseActions expectClosure(UUID accountId) {
        return server.expect(requestTo(accountsUrl() + "/" + accountId + "/closure")).andExpect(method(HttpMethod.POST));
    }

    private String personUrl() { return "http://person/v1/people/" + personId; }
    private String auditUrl() { return personUrl() + "/erasure-audit-events"; }
    private String accountsUrl() { return "http://account/v1/people/" + personId + "/accounts"; }
}
