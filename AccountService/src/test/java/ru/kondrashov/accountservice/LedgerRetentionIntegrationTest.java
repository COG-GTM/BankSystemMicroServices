package ru.kondrashov.accountservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.dto.*;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.AdjustmentsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.repositories.PaymentsRepository;
import ru.kondrashov.accountservice.repositories.TransferRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class LedgerRetentionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private PaymentsRepository paymentsRepository;

    @Autowired
    private AdjustmentsRepository adjustmentsRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Account newAccount() {
        Account account = new Account("retention", LocalDate.now(), UUID.randomUUID());
        accountsService.save(account);
        return account;
    }

    private Bill newBill(Account account, BigDecimal amount) throws Exception {
        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(USD, amount, false, account))))
                .andExpect(status().isCreated());
        return billsRepository.getBillsByAccount_IdAndClosedFalse(account.getId()).stream()
                .filter(bill -> bill.getAmount().compareTo(amount) == 0)
                .findFirst().get();
    }

    private List<FinancialTransactionResponseDTO> history(Account account, Bill bill) throws Exception {
        String body = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions", account.getPersonId(), account.getId(), bill.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, new TypeReference<List<FinancialTransactionResponseDTO>>() {});
    }

    @Test
    void closingAccountRetainsBillsAndTransactionHistoryForBothParties() throws Exception {
        Account customer = newAccount();
        Account counterparty = newAccount();
        Bill customerBill = newBill(customer, new BigDecimal(100));
        Bill counterpartyBill = newBill(counterparty, new BigDecimal(7));

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/payments", customer.getPersonId(), customer.getId(), customerBill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequestDTO(USD, BigDecimal.ONE, LocalDateTime.now(), "Payment", customerBill))))
                .andExpect(status().isAccepted());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/adjustments", customer.getPersonId(), customer.getId(), customerBill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdjustmentRequestDTO(USD, BigDecimal.TEN, LocalDateTime.now(), "Adjustment", customerBill))))
                .andExpect(status().isAccepted());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", customer.getPersonId(), customer.getId(), customerBill.getId(), counterpartyBill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransferRequestDTO(USD, new BigDecimal(5), LocalDateTime.now(), "Transfer", customerBill, counterpartyBill))))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", customer.getPersonId(), customer.getId()))
                .andExpect(status().isNoContent());

        Account closedAccount = accountsRepository.getAccountsById(customer.getId()).get();
        Bill closedBill = billsRepository.getBillById(customerBill.getId()).get();
        assertThat(closedAccount.isClosed()).isTrue();
        assertThat(closedBill.isClosed()).isTrue();
        assertThat(closedBill.getClosedAt()).isEqualTo(closedAccount.getClosedAt());
        assertThat(closedBill.getAmount().compareTo(new BigDecimal(104))).isEqualTo(0);

        assertThat(paymentsRepository.getPaymentsByBill_Id(customerBill.getId())).hasSize(1);
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(customerBill.getId())).hasSize(1);
        assertThat(transferRepository.getTransfersBySourceBill_Id(customerBill.getId())).hasSize(1);
        assertThat(history(customer, customerBill)).hasSize(3);
        assertThat(history(counterparty, counterpartyBill)).hasSize(1);
        assertThat(billsRepository.getBillById(counterpartyBill.getId()).get().isClosed()).isFalse();

        mockMvc.perform(get("/v1/people/{personId}/accounts", customer.getPersonId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills", customer.getPersonId(), customer.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        accountsService.close(counterparty.getId());
    }

    @Test
    void closedBillRejectsNewTransactions() throws Exception {
        Account account = newAccount();
        Account other = newAccount();
        Bill bill = newBill(account, BigDecimal.TEN);
        Bill otherBill = newBill(other, BigDecimal.TEN);

        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", account.getPersonId(), account.getId(), bill.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/payments", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequestDTO(USD, BigDecimal.ONE, LocalDateTime.now(), "Payment", bill))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exception").value("BillClosedException"));
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/adjustments", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdjustmentRequestDTO(USD, BigDecimal.ONE, LocalDateTime.now(), "Adjustment", bill))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", other.getPersonId(), other.getId(), otherBill.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransferRequestDTO(USD, BigDecimal.ONE, LocalDateTime.now(), "Transfer", otherBill, bill))))
                .andExpect(status().isConflict());

        assertThat(billsRepository.getBillById(bill.getId()).get().getAmount().compareTo(BigDecimal.TEN)).isEqualTo(0);
        assertThat(billsRepository.getBillById(otherBill.getId()).get().getAmount().compareTo(BigDecimal.TEN)).isEqualTo(0);
        assertThat(billsRepository.getBillById(bill.getId()).get().isClosed()).isTrue();

        accountsService.close(account.getId());
        accountsService.close(other.getId());
    }

    @Test
    void closedAccountRejectsNewBillsAndCannotBeReopenedThroughBillPayload() throws Exception {
        Account account = newAccount();
        Bill bill = newBill(account, BigDecimal.TEN);
        accountsService.close(account.getId());

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(USD, BigDecimal.ONE, false, account))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.exception").value("AccountClosedException"));

        assertThat(accountsRepository.getAccountsById(account.getId()).get().isClosed()).isTrue();
        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).extracting(Bill::getId).containsExactly(bill.getId());
    }

    @Test
    void databaseRestrictsHardDeletesOfLedgerParents() throws Exception {
        Account account = newAccount();
        Bill bill = newBill(account, BigDecimal.TEN);
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/payments", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequestDTO(USD, BigDecimal.ONE, LocalDateTime.now(), "Payment", bill))))
                .andExpect(status().isAccepted());

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM accounts WHERE id = ?", account.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM bills WHERE id = ?", bill.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(billsRepository.getBillById(bill.getId())).isPresent();
        assertThat(paymentsRepository.getPaymentsByBill_Id(bill.getId())).hasSize(1);

        accountsService.close(account.getId());
    }
}
