package ru.kondrashov.accountservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import ru.kondrashov.accountservice.audit.AuditContextResolver;
import ru.kondrashov.accountservice.audit.BillAuditRecorder;
import ru.kondrashov.accountservice.services.interfaces.AuditService;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.AuditOutcome;
import ru.kondrashov.accountservice.controllers.dto.AdjustmentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.AuditEvent;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = BillsController.class)
@Import({BillAuditRecorder.class, AuditContextResolver.class})
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
    private AuditService auditService;

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


    @Test
    void commitPayment_recordsSuccessAuditEvent() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        Bill bill = new Bill();
        bill.setAmount(new BigDecimal("10"));
        when(billsService.getBill(billId)).thenReturn(bill);
        when(paymentsMapping.mapToPayment(any())).thenReturn(new Payment());

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", personId, accountId, billId)
                .header(AuditContextResolver.ACTOR_HEADER, "alice")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paymentRequest(bill))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(billsService).commitPayment(eq(billId), payment.capture());
        assertThat(payment.getValue().getInitiatedBy()).isEqualTo("alice");

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordSuccess(event.capture());
        verify(auditService, never()).recordFailure(any());
        assertThat(event.getValue().getOperation()).isEqualTo(AuditOperation.PAYMENT);
        assertThat(event.getValue().getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getValue().getActor()).isEqualTo("alice");
        assertThat(event.getValue().getPersonId()).isEqualTo(personId);
        assertThat(event.getValue().getAccountId()).isEqualTo(accountId);
        assertThat(event.getValue().getBillId()).isEqualTo(billId);
        assertThat(event.getValue().getAmount()).isEqualByComparingTo("4");
        assertThat(event.getValue().getCurrency()).isEqualTo("USD");
        assertThat(event.getValue().getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getValue().getRequestId()).isNotBlank();
    }

    @Test
    void commitPayment_withoutActor_returns400AndRecordsRejection() throws Exception {
        UUID billId = UUID.randomUUID();
        when(billsService.getBill(billId)).thenThrow(new BillNotFoundException("missing"));

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paymentRequest(new Bill()))))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitPayment(any(), any());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordFailure(event.capture());
        assertThat(event.getValue().getOutcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.getValue().getActor()).isEqualTo(BillAuditRecorder.UNIDENTIFIED_ACTOR);
        assertThat(event.getValue().getFailureReason()).startsWith("MissingActorException");
    }

    @Test
    void commitPayment_withMalformedActor_isTreatedAsMissing() throws Exception {
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .header(AuditContextResolver.ACTOR_HEADER, "alice\nforged log line")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paymentRequest(new Bill()))))
                .andExpect(status().isBadRequest());

        verify(billsService, never()).commitPayment(any(), any());
    }

    @Test
    void commitPayment_serviceFailure_recordsFailedAuditEvent() throws Exception {
        UUID billId = UUID.randomUUID();
        Bill bill = new Bill();
        bill.setAmount(new BigDecimal("10"));
        when(billsService.getBill(billId)).thenReturn(bill);
        when(paymentsMapping.mapToPayment(any())).thenReturn(new Payment());
        doThrow(new BillNotFoundException("gone")).when(billsService).commitPayment(eq(billId), any());

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .header(AuditContextResolver.ACTOR_HEADER, "alice")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(paymentRequest(bill))))
                .andExpect(status().isNotFound());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordFailure(event.capture());
        verify(auditService, never()).recordSuccess(any());
        assertThat(event.getValue().getOutcome()).isEqualTo(AuditOutcome.FAILED);
        assertThat(event.getValue().getFailureReason()).isEqualTo("BillNotFoundException: gone");
        assertThat(event.getValue().getBalanceAfter()).isEqualByComparingTo("10");
    }

    @Test
    void commitPayment_unexpectedFailure_doesNotStoreForeignExceptionMessage() throws Exception {
        UUID billId = UUID.randomUUID();
        Bill bill = new Bill();
        bill.setAmount(new BigDecimal("10"));
        when(billsService.getBill(billId)).thenReturn(bill);
        when(paymentsMapping.mapToPayment(any())).thenReturn(new Payment());
        doThrow(new IllegalStateException("jdbc:postgresql://db-host/secret-schema")).when(billsService).commitPayment(eq(billId), any());

        try {
            mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", UUID.randomUUID(), UUID.randomUUID(), billId)
                    .header(AuditContextResolver.ACTOR_HEADER, "alice")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(paymentRequest(bill))));
        } catch (Exception ignored) {
            // unhandled exceptions propagate out of MockMvc
        }

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordFailure(event.capture());
        assertThat(event.getValue().getOutcome()).isEqualTo(AuditOutcome.FAILED);
        assertThat(event.getValue().getFailureReason()).isEqualTo("IllegalStateException");
    }

    @Test
    void commitAdjustment_selfApproval_returns403() throws Exception {
        UUID billId = UUID.randomUUID();
        when(adjustmentMapping.mapToAdjustment(any())).thenReturn(new Adjustment());
        AdjustmentRequestDTO request = new AdjustmentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "fix", new Bill(), "Alice");

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .header(AuditContextResolver.ACTOR_HEADER, "alice")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(billsService, never()).commitAdjustment(any(), any());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordFailure(event.capture());
        assertThat(event.getValue().getOperation()).isEqualTo(AuditOperation.ADJUSTMENT);
        assertThat(event.getValue().getOutcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.getValue().getApprovedBy()).isEqualTo("Alice");
    }

    @Test
    void commitAdjustment_withApprover_setsMakerAndChecker() throws Exception {
        UUID billId = UUID.randomUUID();
        when(adjustmentMapping.mapToAdjustment(any())).thenReturn(new Adjustment());
        AdjustmentRequestDTO request = new AdjustmentRequestDTO(Currency.getInstance("USD"), BigDecimal.ONE, LocalDateTime.now(), "fix", new Bill(), "bob");

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", UUID.randomUUID(), UUID.randomUUID(), billId)
                .header(AuditContextResolver.ACTOR_HEADER, "alice")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<Adjustment> adjustment = ArgumentCaptor.forClass(Adjustment.class);
        verify(billsService).commitAdjustment(eq(billId), adjustment.capture());
        assertThat(adjustment.getValue().getInitiatedBy()).isEqualTo("alice");
        assertThat(adjustment.getValue().getApprovedBy()).isEqualTo("bob");
        verify(auditService).recordSuccess(any());
    }

    private static PaymentRequestDTO paymentRequest(Bill bill) {
        return new PaymentRequestDTO(Currency.getInstance("USD"), new BigDecimal("4"), LocalDateTime.now(), "pay", bill);
    }
}
