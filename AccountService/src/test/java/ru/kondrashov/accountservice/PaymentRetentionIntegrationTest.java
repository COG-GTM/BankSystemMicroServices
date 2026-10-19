package ru.kondrashov.accountservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.*;
import ru.kondrashov.accountservice.services.FinancialRecordRetentionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class PaymentRetentionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountsRepository accountsRepository;
    @Autowired private BillsRepository billsRepository;
    @Autowired private PaymentsRepository paymentsRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private RetentionAuditRepository auditRepository;
    @Autowired private FinancialRecordRetentionService retentionService;

    private Account account() {
        return accountsRepository.save(new Account("retention", LocalDate.now(), UUID.randomUUID()));
    }

    private Bill bill(Account account) {
        return billsRepository.save(new Bill(USD, new BigDecimal("100"), true, account));
    }

    private Payment payment(Bill bill) {
        return paymentsRepository.save(new Payment(USD, new BigDecimal("5"), LocalDateTime.now(), "Payment for order #42", bill));
    }

    private String billUrl(Account account) {
        return "/v1/people/" + account.getPersonId() + "/accounts/" + account.getId() + "/bills";
    }

    @Test
    void closingBillKeepsPaymentHistory() throws Exception {
        Account account = account();
        Bill bill = bill(account);
        Payment payment = payment(bill);

        mockMvc.perform(delete(billUrl(account) + "/{id}", bill.getId())).andExpect(status().isNoContent());

        assertThat(paymentsRepository.findById(payment.getId())).isPresent();
        assertThat(billsRepository.getBillById(bill.getId()).get().getClosed()).isTrue();
        assertThat(auditRepository.findAllByEntityId(bill.getId()))
                .extracting(RetentionAuditRecord::getAction).containsExactly(RetentionAuditRecord.Action.BILL_CLOSED);

        String body = mockMvc.perform(get(billUrl(account) + "/{id}/transactions", bill.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<FinancialTransactionResponseDTO> history = objectMapper.readValue(body, new TypeReference<List<FinancialTransactionResponseDTO>>() {});
        assertThat(history).extracting(FinancialTransactionResponseDTO::getMessage).containsExactly("Payment for order #42");
    }

    @Test
    void closedBillRejectsNewPayments() throws Exception {
        Account account = account();
        Bill bill = bill(account);
        mockMvc.perform(delete(billUrl(account) + "/{id}", bill.getId())).andExpect(status().isNoContent());

        PaymentRequestDTO request = new PaymentRequestDTO(USD, new BigDecimal("1"), LocalDateTime.now(), "late", bill);
        mockMvc.perform(put(billUrl(account) + "/{id}/payments", bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        assertThat(paymentsRepository.getPaymentsByBill_Id(bill.getId())).isEmpty();
    }

    @Test
    void closingAccountClosesBillsAndKeepsPayments() throws Exception {
        Account account = account();
        Bill bill = bill(account);
        Payment payment = payment(bill);

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", account.getPersonId(), account.getId()))
                .andExpect(status().isNoContent());

        assertThat(accountsRepository.getAccountsById(account.getId()).get().getClosed()).isTrue();
        assertThat(billsRepository.getBillById(bill.getId()).get().getClosed()).isTrue();
        assertThat(paymentsRepository.findById(payment.getId())).isPresent();
    }

    @Test
    void databaseRefusesToDeleteBillWithPayments() {
        Bill bill = bill(account());
        payment(bill);

        assertThatThrownBy(() -> {
            billsRepository.delete(bill);
            billsRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(billsRepository.getBillById(bill.getId())).isPresent();
    }

    @Test
    void purgeOnlyRemovesRecordsPastRetentionAndAuditsThem() throws Exception {
        Account account = account();
        Bill bill = bill(account);
        Payment payment = payment(bill);
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", account.getPersonId(), account.getId()))
                .andExpect(status().isNoContent());

        retentionService.purgeClosedBefore(LocalDateTime.now().minusYears(5));
        assertThat(paymentsRepository.findById(payment.getId())).isPresent();
        assertThat(billsRepository.findById(bill.getId())).isPresent();

        retentionService.purgeClosedBefore(LocalDateTime.now().plusMinutes(1));
        assertThat(paymentsRepository.findById(payment.getId())).isEmpty();
        assertThat(billsRepository.findById(bill.getId())).isEmpty();
        assertThat(accountsRepository.findById(account.getId())).isEmpty();
        for (UUID id : new UUID[]{payment.getId(), bill.getId(), account.getId()}) {
            assertThat(auditRepository.findAllByEntityId(id))
                    .extracting(RetentionAuditRecord::getAction).contains(RetentionAuditRecord.Action.RECORD_PURGED);
        }
    }

    @Test
    void purgeKeepsTransfersWhoseCounterpartyBillIsStillOpen() throws Exception {
        Account account = account();
        Bill closedBill = bill(account);
        Bill openBill = bill(account);
        Transfer transfer = transferRepository.save(new Transfer(USD, new BigDecimal("3"), LocalDateTime.now(), "Transfer", closedBill, openBill));
        mockMvc.perform(delete(billUrl(account) + "/{id}", closedBill.getId())).andExpect(status().isNoContent());

        retentionService.purgeClosedBefore(LocalDateTime.now().plusMinutes(1));

        assertThat(transferRepository.findById(transfer.getId())).isPresent();
        assertThat(billsRepository.findById(closedBill.getId())).isPresent();
        assertThat(billsRepository.findById(openBill.getId())).isPresent();
    }
}
