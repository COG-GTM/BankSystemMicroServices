package ru.kondrashov.accountservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = BillsController.class)
class BillsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillsService billsService;

    @MockBean
    private BillsMapping billsMapping;

    @MockBean
    private PaymentsMapping paymentsMapping;

    @MockBean
    private AdjustmentMapping adjustmentMapping;

    @MockBean
    private TransferMapping transferMapping;

    @MockBean
    private Logger logger;

    private Bill billOf(UUID personId, UUID accountId, UUID billId) {
        Account account = new Account("name", LocalDate.now(), personId);
        account.setId(accountId);
        Bill bill = new Bill(Currency.getInstance("USD"), BigDecimal.TEN, false, account);
        bill.setId(billId);
        return bill;
    }

    @Test
    void whenGetBillById_thenReturnsSingleBillWithoutListing() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        Bill bill = billOf(personId, accountId, billId);
        when(billsService.getBill(billId)).thenReturn(bill);
        when(billsMapping.mapToBillResponseDTO(bill)).thenReturn(
                new BillResponseDTO(billId, bill.getCurrency(), bill.getAmount(), bill.getIsOverdraft(), bill.getAccount()));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}", personId, accountId, billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(billId.toString()))
                .andExpect(jsonPath("$.account.id").value(accountId.toString()));

        verify(billsService, never()).getBillsByAccountId(any());
    }

    @Test
    void whenGetBillOfAnotherAccount_thenReturns404() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        when(billsService.getBill(billId)).thenReturn(billOf(personId, UUID.randomUUID(), billId));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}", personId, UUID.randomUUID(), billId))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenGetBillOfAnotherPerson_thenReturns404() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        when(billsService.getBill(billId)).thenReturn(billOf(UUID.randomUUID(), accountId, billId));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}", UUID.randomUUID(), accountId, billId))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenGetMissingBill_thenReturns404() throws Exception {
        UUID billId = UUID.randomUUID();
        when(billsService.getBill(billId)).thenThrow(new BillNotFoundException("Bill with id = "+billId+" not found."));

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}", UUID.randomUUID(), UUID.randomUUID(), billId))
                .andExpect(status().isNotFound());
    }

    @Test
    void whenValidUrlAndMethodAndContentType_thenReturns201() throws Exception {

        BillRequestDTO bill = new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.TEN, false, new Account("name", LocalDate.now(), UUID.randomUUID()));

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID() )
                .content(objectMapper.writeValueAsString(bill))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    void whenNullValue_thenReturns400() throws Exception {

        BillRequestDTO bill = new BillRequestDTO(null, BigDecimal.TEN, false, new Account("name", LocalDate.now(), UUID.randomUUID()));

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID())
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
                .contentType("application/json")
                .content(request))
                .andExpect(status().isBadRequest());
    }

}