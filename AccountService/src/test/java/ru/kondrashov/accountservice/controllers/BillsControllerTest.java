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
import ru.kondrashov.accountservice.controllers.dto.AdjustmentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.TransferRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final String BILLS_URL = "/v1/people/{personId}/accounts/{accountId}/bills";

    private Bill billWithId(UUID id) {
        Bill bill = new Bill(Currency.getInstance("USD"), BigDecimal.TEN, false, new Account("name", LocalDate.now(), UUID.randomUUID()));
        bill.setId(id);
        return bill;
    }

    @Test
    void commitPayment_whenBodyBillDiffersFromPath_thenReturns400AndDoesNotCommit() throws Exception {
        UUID billId = UUID.randomUUID();
        PaymentRequestDTO payment = new PaymentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "payment", billWithId(UUID.randomUUID()));

        mockMvc.perform(put(BILLS_URL + "/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitPayment(any(), any());
    }

    @Test
    void commitPayment_whenBodyBillMatchesPath_thenReturns202() throws Exception {
        UUID billId = UUID.randomUUID();
        PaymentRequestDTO payment = new PaymentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "payment", billWithId(billId));

        mockMvc.perform(put(BILLS_URL + "/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isAccepted());

        verify(billsService).commitPayment(eq(billId), any());
    }

    @Test
    void commitPayment_whenBodyHasNoBill_thenReturns202() throws Exception {
        UUID billId = UUID.randomUUID();
        PaymentRequestDTO payment = new PaymentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "payment", null);

        mockMvc.perform(put(BILLS_URL + "/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isAccepted());

        verify(billsService).commitPayment(eq(billId), any());
    }

    @Test
    void commitAdjustment_whenBodyBillDiffersFromPath_thenReturns400AndDoesNotCommit() throws Exception {
        UUID billId = UUID.randomUUID();
        AdjustmentRequestDTO adjustment = new AdjustmentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "adjustment", billWithId(UUID.randomUUID()));

        mockMvc.perform(put(BILLS_URL + "/{id}/adjustments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(adjustment)))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitAdjustment(any(), any());
    }

    @Test
    void commitTransfer_whenBodySourceBillDiffersFromPath_thenReturns400AndDoesNotCommit() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        TransferRequestDTO transfer = new TransferRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "transfer", billWithId(UUID.randomUUID()), billWithId(beneficiaryId));

        mockMvc.perform(put(BILLS_URL + "/{sourceId}/transfer/{beneficiaryBillId}", UUID.randomUUID(), UUID.randomUUID(), sourceId, beneficiaryId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitTransfer(any(), any(), any());
    }

    @Test
    void commitTransfer_whenBodyBeneficiaryBillDiffersFromPath_thenReturns400AndDoesNotCommit() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        TransferRequestDTO transfer = new TransferRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "transfer", billWithId(sourceId), billWithId(UUID.randomUUID()));

        mockMvc.perform(put(BILLS_URL + "/{sourceId}/transfer/{beneficiaryBillId}", UUID.randomUUID(), UUID.randomUUID(), sourceId, beneficiaryId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitTransfer(any(), any(), any());
    }

    @Test
    void commitTransfer_whenBodyBillsMatchPath_thenReturns202() throws Exception {
        UUID sourceId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        TransferRequestDTO transfer = new TransferRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "transfer", billWithId(sourceId), billWithId(beneficiaryId));

        mockMvc.perform(put(BILLS_URL + "/{sourceId}/transfer/{beneficiaryBillId}", UUID.randomUUID(), UUID.randomUUID(), sourceId, beneficiaryId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isAccepted());

        verify(billsService).commitTransfer(eq(sourceId), eq(beneficiaryId), any());
    }
}
