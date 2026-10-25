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
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.TransferRequestDTO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        BillRequestDTO bill = new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.TEN, false);

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), UUID.randomUUID() )
                .content(objectMapper.writeValueAsString(bill))
                .contentType("application/json"))
                .andExpect(status().isCreated());
    }

    @Test
    void whenNullValue_thenReturns400() throws Exception {

        BillRequestDTO bill = new BillRequestDTO(null, BigDecimal.TEN, false);

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

    @Test
    void commitPayment_usesPathIdsAndIgnoresBodyBill() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        Payment payment = new Payment();
        when(paymentsMapping.mapToPayment(any(PaymentRequestDTO.class))).thenReturn(payment);

        String request = "{\"currency\":\"USD\",\"amount\":5,\"message\":\"Payment\"," +
                "\"bill\":{\"id\":\"" + UUID.randomUUID() + "\"}}";

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", personId, accountId, billId)
                .contentType("application/json")
                .content(request))
                .andExpect(status().isAccepted());

        verify(billsService).commitPayment(eq(personId), eq(accountId), eq(billId), eq(payment));
    }

    @Test
    void commitPayment_withoutBodyBill_thenReturns202() throws Exception {
        PaymentRequestDTO payment = new PaymentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, null, "Payment");

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isAccepted());
    }

    @Test
    void commitTransfer_usesPathIds() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        Transfer transfer = new Transfer();
        when(transferMapping.mapToTransfer(any(TransferRequestDTO.class))).thenReturn(transfer);
        TransferRequestDTO request = new TransferRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, null, "Transfer");

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", personId, accountId, sourceId, beneficiaryId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        verify(billsService).commitTransfer(eq(personId), eq(accountId), eq(sourceId), eq(beneficiaryId), eq(transfer));
    }

    @Test
    void getTransactions_billOutsideAccount_thenReturns404() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        doThrow(new BillNotFoundException("not found")).when(billsService).getBill(personId, accountId, billId);

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions", personId, accountId, billId))
                .andExpect(status().isNotFound());
    }
}
