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
import ru.kondrashov.commonservice.controllers.dto.AccountRequestDTO;
import ru.kondrashov.commonservice.controllers.dto.BillRequestDTO;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CommonServiceImplTest {

    private static final String ACCOUNT_URL = "http://accounts/v1/people/%s/accounts/%s";
    private static final String BILL_URL = "http://accounts/v1/people/%s/accounts/%s/bills/%s";

    private MockRestServiceServer server;
    private CommonServiceImpl commonService;

    private final UUID personId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID billId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        commonService = new CommonServiceImpl(restTemplate, LogManager.getRootLogger());
        ReflectionTestUtils.setField(commonService, "accountUrl", ACCOUNT_URL);
        ReflectionTestUtils.setField(commonService, "billUrl", BILL_URL);
    }

    @Test
    void getAccountRequestsSingleAccountById() {
        server.expect(requestTo(String.format(ACCOUNT_URL, personId, accountId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + accountId + "\",\"name\":\"main\",\"creationDate\":\"2021-04-23\",\"personId\":\"" + personId + "\"}", MediaType.APPLICATION_JSON));

        AccountRequestDTO account = commonService.getAccount(personId, accountId);

        assertEquals(accountId, account.getId());
        assertEquals(personId, account.getPersonId());
        server.verify();
    }

    @Test
    void getAccountMapsNotFoundToRuntimeException() {
        server.expect(requestTo(String.format(ACCOUNT_URL, personId, accountId)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RuntimeException e = assertThrows(RuntimeException.class, () -> commonService.getAccount(personId, accountId));

        assertEquals("Person with id " + personId + " dont have account with id " + accountId, e.getMessage());
    }

    @Test
    void getBillRequestsSingleBillById() {
        server.expect(requestTo(String.format(BILL_URL, personId, accountId, billId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + billId + "\",\"currency\":\"USD\",\"amount\":10.00,\"isOverdraft\":false,"
                        + "\"account\":{\"id\":\"" + accountId + "\",\"name\":\"main\",\"creationDate\":\"2021-04-23\",\"personId\":\"" + personId + "\"}}", MediaType.APPLICATION_JSON));

        BillRequestDTO bill = commonService.getBill(personId, accountId, billId);

        assertEquals(billId, bill.getId());
        assertEquals(accountId, bill.getAccount().getId());
        server.verify();
    }

    @Test
    void getBillMapsNotFoundToRuntimeException() {
        server.expect(requestTo(String.format(BILL_URL, personId, accountId, billId)))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        RuntimeException e = assertThrows(RuntimeException.class, () -> commonService.getBill(personId, accountId, billId));

        assertEquals("Account with id " + accountId + " dont have bill with id " + billId, e.getMessage());
    }
}
