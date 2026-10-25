package ru.kondrashov.accountservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.BillsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class BillPathScopingIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private BillsService billsService;

    private final List<UUID> accountIds = new ArrayList<>();

    private Account account;
    private Account otherAccount;
    private Bill bill;
    private Bill siblingBill;
    private Bill otherBill;

    @BeforeEach
    void setUp() {
        account = account(UUID.randomUUID());
        otherAccount = account(UUID.randomUUID());
        bill = bill(account, 100);
        siblingBill = bill(account, 100);
        otherBill = bill(otherAccount, 100);
    }

    @AfterEach
    void tearDown() {
        accountIds.forEach(accountsRepository::deleteById);
    }

    @Test
    void payment_bodyBillIsIgnored_ledgerFollowsPath() throws Exception {
        putJson(paymentsUrl(account, bill.getId()), transaction("bill", otherBill.getId()))
                .andExpect(status().isAccepted());

        assertThat(amount(bill)).isEqualByComparingTo("95");
        assertThat(amount(otherBill)).isEqualByComparingTo("100");
        Collection<Payment> payments = billsService.getPaymentsByBillId(bill.getId());
        assertThat(payments).hasSize(1);
        assertThat(payments.iterator().next().getBill().getId()).isEqualTo(bill.getId());
        assertThat(billsService.getPaymentsByBillId(otherBill.getId())).isEmpty();
    }

    @Test
    void adjustment_bodyBillIsIgnored_ledgerFollowsPath() throws Exception {
        putJson(billUrl(account, bill.getId()) + "/adjustments", transaction("bill", siblingBill.getId()))
                .andExpect(status().isAccepted());

        assertThat(amount(bill)).isEqualByComparingTo("105");
        assertThat(amount(siblingBill)).isEqualByComparingTo("100");
        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).hasSize(1);
        assertThat(billsService.getAdjustmentsByBillId(siblingBill.getId())).isEmpty();
    }

    @Test
    void transfer_bodyBillsAreIgnored_ledgerFollowsPath() throws Exception {
        String body = "{\"currency\":\"USD\",\"amount\":5,\"message\":\"Transfer\"," +
                "\"sourceBill\":{\"id\":\"" + otherBill.getId() + "\"}," +
                "\"beneficiaryBill\":{\"id\":\"" + bill.getId() + "\"}}";

        putJson(billUrl(account, bill.getId()) + "/transfer/" + siblingBill.getId(), body)
                .andExpect(status().isAccepted());

        assertThat(amount(bill)).isEqualByComparingTo("95");
        assertThat(amount(siblingBill)).isEqualByComparingTo("105");
        assertThat(amount(otherBill)).isEqualByComparingTo("100");
        Transfer transfer = billsService.getTransfersBySourceBillId(bill.getId()).iterator().next();
        assertThat(transfer.getSourceBill().getId()).isEqualTo(bill.getId());
        assertThat(transfer.getBeneficiaryBill().getId()).isEqualTo(siblingBill.getId());
        assertThat(billsService.getTransfersBySourceBillId(otherBill.getId())).isEmpty();
    }

    @Test
    void transfer_toBillOfAnotherAccount_isAllowed() throws Exception {
        putJson(billUrl(account, bill.getId()) + "/transfer/" + otherBill.getId(), transaction(null, null))
                .andExpect(status().isAccepted());

        assertThat(amount(bill)).isEqualByComparingTo("95");
        assertThat(amount(otherBill)).isEqualByComparingTo("105");
    }

    @Test
    void transfer_sourceOutsidePathAccount_returns404() throws Exception {
        putJson(billUrl(account, otherBill.getId()) + "/transfer/" + bill.getId(), transaction(null, null))
                .andExpect(status().isNotFound());

        assertThat(amount(otherBill)).isEqualByComparingTo("100");
        assertThat(amount(bill)).isEqualByComparingTo("100");
        assertThat(billsService.getTransfersBySourceBillId(otherBill.getId())).isEmpty();
    }

    @Test
    void payment_billOutsidePathAccount_returns404() throws Exception {
        putJson(paymentsUrl(account, otherBill.getId()), transaction(null, null))
                .andExpect(status().isNotFound());

        assertThat(amount(otherBill)).isEqualByComparingTo("100");
        assertThat(billsService.getPaymentsByBillId(otherBill.getId())).isEmpty();
    }

    @Test
    void payment_accountOfAnotherPerson_returns404() throws Exception {
        String url = "/v1/people/" + otherAccount.getPersonId() + "/accounts/" + account.getId() + "/bills/" + bill.getId() + "/payments";

        putJson(url, transaction(null, null)).andExpect(status().isNotFound());

        assertThat(amount(bill)).isEqualByComparingTo("100");
    }

    @Test
    void adjustment_billOutsidePathAccount_returns404() throws Exception {
        putJson(billUrl(account, otherBill.getId()) + "/adjustments", transaction(null, null))
                .andExpect(status().isNotFound());

        assertThat(amount(otherBill)).isEqualByComparingTo("100");
    }

    @Test
    void transactions_billOutsidePathAccount_returns404() throws Exception {
        mockMvc.perform(get(billUrl(account, otherBill.getId()) + "/transactions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bills_accountOfAnotherPerson_returns404() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills", otherAccount.getPersonId(), account.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void save_usesPathAccountAndIgnoresBodyAccount() throws Exception {
        String body = "{\"currency\":\"EUR\",\"amount\":1,\"isOverdraft\":false," +
                "\"account\":{\"id\":\"" + otherAccount.getId() + "\",\"name\":\"x\",\"creationDate\":\"2021-04-23\",\"personId\":\"" + otherAccount.getPersonId() + "\"}}";

        mockMvc.perform(post(billsUrl(account)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).hasSize(3);
        assertThat(billsRepository.getBillsByAccount_Id(otherAccount.getId())).hasSize(1);
    }

    @Test
    void save_accountOfAnotherPerson_returns404() throws Exception {
        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", otherAccount.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"EUR\",\"amount\":1,\"isOverdraft\":false}"))
                .andExpect(status().isNotFound());

        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).hasSize(2);
    }

    @Test
    void update_doesNotMoveBillToBodyAccount() throws Exception {
        String body = "{\"currency\":\"USD\",\"amount\":7,\"isOverdraft\":true," +
                "\"account\":{\"id\":\"" + otherAccount.getId() + "\",\"name\":\"x\",\"creationDate\":\"2021-04-23\",\"personId\":\"" + otherAccount.getPersonId() + "\"}}";

        putJson(billUrl(account, bill.getId()), body).andExpect(status().isAccepted());

        Bill updated = billsRepository.getBillById(bill.getId()).get();
        assertThat(updated.getAccount().getId()).isEqualTo(account.getId());
        assertThat(updated.getAmount()).isEqualByComparingTo("7");
    }

    @Test
    void updateAndDelete_billOutsidePathAccount_return404() throws Exception {
        putJson(billUrl(account, otherBill.getId()), "{\"currency\":\"USD\",\"amount\":0,\"isOverdraft\":true}")
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(billUrl(account, otherBill.getId())))
                .andExpect(status().isNotFound());

        assertThat(amount(otherBill)).isEqualByComparingTo("100");
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String url, String body) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String transaction(String billField, UUID billId) {
        String bodyBill = billField == null ? "" : ",\"" + billField + "\":{\"id\":\"" + billId + "\"}";
        return "{\"currency\":\"USD\",\"amount\":5,\"message\":\"Txn\"" + bodyBill + "}";
    }

    private String billsUrl(Account account) {
        return "/v1/people/" + account.getPersonId() + "/accounts/" + account.getId() + "/bills";
    }

    private String billUrl(Account account, UUID billId) {
        return billsUrl(account) + "/" + billId;
    }

    private String paymentsUrl(Account account, UUID billId) {
        return billUrl(account, billId) + "/payments";
    }

    private BigDecimal amount(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }

    private Account account(UUID personId) {
        Account saved = accountsRepository.save(new Account("account", LocalDate.now(), personId));
        accountIds.add(saved.getId());
        return saved;
    }

    private Bill bill(Account account, int amount) {
        return billsRepository.save(new Bill(USD, BigDecimal.valueOf(amount), false, account));
    }
}
