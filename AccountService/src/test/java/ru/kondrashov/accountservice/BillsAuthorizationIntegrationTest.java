package ru.kondrashov.accountservice;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.kondrashov.accountservice.controllers.dto.AdjustmentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.TransferRequestDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class BillsAuthorizationIntegrationTest {

    private static final UUID CUSTOMER_PERSON_ID = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
    private static final Currency USD = Currency.getInstance("USD");
    private static final BigDecimal INITIAL = new BigDecimal("100.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private BillsRepository billsRepository;

    private Account customerAccount;
    private Account otherAccount;
    private Bill customerBill;
    private Bill customerSecondBill;
    private Bill otherBill;

    @BeforeEach
    void setUp() {
        customerAccount = accountsRepository.save(new Account("customer", LocalDate.now(), CUSTOMER_PERSON_ID));
        otherAccount = accountsRepository.save(new Account("other", LocalDate.now(), UUID.randomUUID()));
        customerBill = billsRepository.save(new Bill(USD, INITIAL, false, customerAccount));
        customerSecondBill = billsRepository.save(new Bill(USD, INITIAL, false, customerAccount));
        otherBill = billsRepository.save(new Bill(USD, INITIAL, false, otherAccount));
    }

    @AfterEach
    void tearDown() {
        accountsRepository.deleteAll(java.util.Arrays.asList(customerAccount, otherAccount));
    }

    private static RequestPostProcessor admin() {
        return httpBasic("test-admin", "test-admin-password");
    }

    private static RequestPostProcessor customer() {
        return httpBasic("test-customer", "test-customer-password");
    }

    private MockHttpServletRequestBuilder adjustment(Account pathAccount, UUID pathPersonId, Bill bill) throws Exception {
        AdjustmentRequestDTO dto = new AdjustmentRequestDTO(USD, new BigDecimal("1000"), LocalDateTime.now(), "Adjustment", bill);
        return put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/adjustments", pathPersonId, pathAccount.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto));
    }

    private MockHttpServletRequestBuilder payment(Account pathAccount, Bill bill) throws Exception {
        PaymentRequestDTO dto = new PaymentRequestDTO(USD, new BigDecimal("10"), LocalDateTime.now(), "Payment", bill);
        return put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", pathAccount.getPersonId(), pathAccount.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto));
    }

    private MockHttpServletRequestBuilder transfer(Account pathAccount, Bill source, Bill beneficiary) throws Exception {
        TransferRequestDTO dto = new TransferRequestDTO(USD, new BigDecimal("10"), LocalDateTime.now(), "Transfer", source, beneficiary);
        return put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}",
                pathAccount.getPersonId(), pathAccount.getId(), source.getId(), beneficiary.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto));
    }

    private BigDecimal balance(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(adjustment(customerAccount, CUSTOMER_PERSON_ID, customerBill)).andExpect(status().isUnauthorized());
        mockMvc.perform(payment(customerAccount, customerBill)).andExpect(status().isUnauthorized());
        mockMvc.perform(transfer(customerAccount, customerBill, otherBill)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", CUSTOMER_PERSON_ID, customerAccount.getId(), customerBill.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills", CUSTOMER_PERSON_ID, customerAccount.getId()))
                .andExpect(status().isUnauthorized());

        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
        assertThat(balance(otherBill)).isEqualByComparingTo(INITIAL);
        assertThat(billsRepository.getBillById(customerBill.getId())).isPresent();
    }

    @Test
    void wrongCredentialsAreRejected() throws Exception {
        mockMvc.perform(adjustment(customerAccount, CUSTOMER_PERSON_ID, customerBill).with(httpBasic("test-admin", "wrong")))
                .andExpect(status().isUnauthorized());
        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
    }

    @Test
    void customerCannotAdjustOrManageBills() throws Exception {
        mockMvc.perform(adjustment(customerAccount, CUSTOMER_PERSON_ID, customerBill).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", CUSTOMER_PERSON_ID, customerAccount.getId(), customerBill.getId())
                .with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", CUSTOMER_PERSON_ID, customerAccount.getId())
                .with(customer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(USD, new BigDecimal("1000000"), true, customerAccount))))
                .andExpect(status().isForbidden());

        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
        assertThat(billsRepository.getBillsByAccount_Id(customerAccount.getId())).hasSize(2);
    }

    @Test
    void customerCanPayAndTransferFromOwnBill() throws Exception {
        mockMvc.perform(payment(customerAccount, customerBill).with(customer())).andExpect(status().isAccepted());
        mockMvc.perform(transfer(customerAccount, customerBill, otherBill).with(customer())).andExpect(status().isAccepted());

        assertThat(balance(customerBill)).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(balance(otherBill)).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void customerCannotActOnAnotherPerson() throws Exception {
        mockMvc.perform(payment(otherAccount, otherBill).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(transfer(otherAccount, otherBill, customerBill).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions", otherAccount.getPersonId(), otherAccount.getId(), otherBill.getId())
                .with(customer())).andExpect(status().isForbidden());

        assertThat(balance(otherBill)).isEqualByComparingTo(INITIAL);
        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
    }

    @Test
    void customerCannotUseOwnPathToMoveAnotherPersonsBill() throws Exception {
        mockMvc.perform(payment(customerAccount, otherBill).with(customer())).andExpect(status().isNotFound());
        mockMvc.perform(transfer(customerAccount, otherBill, customerBill).with(customer())).andExpect(status().isNotFound());

        assertThat(balance(otherBill)).isEqualByComparingTo(INITIAL);
        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
    }

    @Test
    void pathOwnershipIsEnforcedForAdmin() throws Exception {
        mockMvc.perform(adjustment(customerAccount, UUID.randomUUID(), customerBill).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(adjustment(customerAccount, CUSTOMER_PERSON_ID, otherBill).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(transfer(customerAccount, otherBill, customerBill).with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", CUSTOMER_PERSON_ID, customerAccount.getId(), otherBill.getId())
                .with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions", CUSTOMER_PERSON_ID, customerAccount.getId(), otherBill.getId())
                .with(admin())).andExpect(status().isNotFound());

        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
        assertThat(balance(otherBill)).isEqualByComparingTo(INITIAL);
        assertThat(billsRepository.getBillById(otherBill.getId())).isPresent();
    }

    @Test
    void adminCanAdjustAndTransferWithConsistentPath() throws Exception {
        mockMvc.perform(adjustment(customerAccount, CUSTOMER_PERSON_ID, customerBill).with(admin())).andExpect(status().isAccepted());
        mockMvc.perform(transfer(customerAccount, customerBill, customerSecondBill).with(admin())).andExpect(status().isAccepted());

        assertThat(balance(customerBill)).isEqualByComparingTo(new BigDecimal("1090.00"));
        assertThat(balance(customerSecondBill)).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void transferToSameBillIsRejected() throws Exception {
        mockMvc.perform(transfer(customerAccount, customerBill, customerBill).with(admin())).andExpect(status().isBadRequest());
        assertThat(balance(customerBill)).isEqualByComparingTo(INITIAL);
    }
}
