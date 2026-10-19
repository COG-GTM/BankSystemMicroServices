package ru.kondrashov.accountservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import ru.kondrashov.accountservice.config.ApplicationSpringConfig;
import ru.kondrashov.accountservice.config.SecurityConfig;
import ru.kondrashov.accountservice.security.PersonAccess;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.kondrashov.accountservice.support.TestJwts.personToken;
import static ru.kondrashov.accountservice.support.TestJwts.serviceToken;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = BillsController.class)
@Import({SecurityConfig.class, PersonAccess.class, ApplicationSpringConfig.class})
class BillsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillsService billsService;

    @MockBean
    private AccountsService accountsService;

    @MockBean
    private BillsMapping billsMapping;

    @MockBean
    private PaymentsMapping paymentsMapping;

    @MockBean
    private AdjustmentMapping adjustmentMapping;

    @MockBean
    private TransferMapping transferMapping;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void whenValidUrlAndMethodAndContentType_thenReturns201() throws Exception {

        BillRequestDTO bill = new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.TEN, false, new Account("name", LocalDate.now(), UUID.randomUUID()));

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID() )
                .with(serviceToken())
                .content(objectMapper.writeValueAsString(bill))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    void whenNullValue_thenReturns400() throws Exception {

        BillRequestDTO bill = new BillRequestDTO(null, BigDecimal.TEN, false, new Account("name", LocalDate.now(), UUID.randomUUID()));

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID())
                .with(serviceToken())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(bill)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenIncorrectValue_thenReturns400() throws Exception {

        String request = "{\n" +
                "  \"account\": {\n" +
                "    \"creationDate\": \"2021-04-23\",\n" +
                "    \"id\": \""+UUID.randomUUID()+"\",\n" +
                "    \"name\": \"string\",\n" +
                "    \"personId\": \""+UUID.randomUUID()+"\"\n" +
                "  },\n" +
                "  \"amount\": 0,\n" +
                "  \"currency\": \"string\",\n" +
                "  \"isOverdraft\": true\n" +
                "}";

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID())
                .with(serviceToken())
                .contentType("application/json")
                .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billOperationsOnAccountOfAnotherPersonReturn404() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID foreignAccountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        when(accountsService.getAccount(personId, foreignAccountId))
                .thenThrow(new AccountNotFoundException("not found"));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills", personId, foreignAccountId)
                .with(personToken(personId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", personId, foreignAccountId, billId)
                .with(personToken(personId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions", personId, foreignAccountId, billId)
                .with(personToken(personId)))
                .andExpect(status().isNotFound());

        verify(billsService, never()).getBillsByAccountId(any());
        verify(billsService, never()).delete(any());
        verify(billsService, never()).getPaymentsByBillId(any());
    }

    @Test
    void billOfAnotherAccountIsNotReachableThroughOwnAccountPath() throws Exception {
        UUID personId = UUID.randomUUID();
        Account ownAccount = account(UUID.randomUUID(), personId);
        Account foreignAccount = account(UUID.randomUUID(), UUID.randomUUID());
        Bill foreignBill = new Bill(Currency.getInstance("USD"), BigDecimal.TEN, false, foreignAccount);
        foreignBill.setId(UUID.randomUUID());
        when(accountsService.getAccount(personId, ownAccount.getId())).thenReturn(ownAccount);
        when(billsService.getBill(foreignBill.getId())).thenReturn(foreignBill);

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", personId, ownAccount.getId(), foreignBill.getId())
                .with(personToken(personId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/payments", personId, ownAccount.getId(), foreignBill.getId())
                .with(personToken(personId))
                .contentType("application/json")
                .content("{\"amount\": 5, \"currency\": \"USD\", \"message\": \"m\", \"bill\": {\"id\": \"" + foreignBill.getId() + "\"}}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}",
                personId, ownAccount.getId(), foreignBill.getId(), UUID.randomUUID())
                .with(personToken(personId))
                .contentType("application/json")
                .content("{\"amount\": 5, \"currency\": \"USD\", \"message\": \"m\", \"sourceBill\": {\"id\": \"" + foreignBill.getId()
                        + "\"}, \"beneficiaryBill\": {\"id\": \"" + UUID.randomUUID() + "\"}}"))
                .andExpect(status().isNotFound());

        verify(billsService, never()).delete(any());
        verify(billsService, never()).commitPayment(any(), any());
        verify(billsService, never()).commitTransfer(any(), any(), any());
    }

    @Test
    void createdBillIsAttachedToOwnedPathAccountNotBodyAccount() throws Exception {
        UUID personId = UUID.randomUUID();
        Account ownAccount = account(UUID.randomUUID(), personId);
        Account foreignAccount = account(UUID.randomUUID(), UUID.randomUUID());
        Bill mapped = new Bill(Currency.getInstance("USD"), BigDecimal.TEN, false, foreignAccount);
        when(accountsService.getAccount(personId, ownAccount.getId())).thenReturn(ownAccount);
        when(billsMapping.mapToBill(any())).thenReturn(mapped);

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", personId, ownAccount.getId())
                .with(personToken(personId))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.TEN, false, foreignAccount))))
                .andExpect(status().isCreated());

        verify(billsService).save(eq(mapped));
        org.assertj.core.api.Assertions.assertThat(mapped.getAccount()).isSameAs(ownAccount);
    }

    private static Account account(UUID id, UUID personId) {
        Account account = new Account("name", LocalDate.now(), personId);
        account.setId(id);
        return account;
    }
}
