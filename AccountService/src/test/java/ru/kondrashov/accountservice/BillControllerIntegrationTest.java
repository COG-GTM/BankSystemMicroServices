package ru.kondrashov.accountservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import ru.kondrashov.accountservice.controllers.dto.*;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.JavaMonetaApiCurrencyExchange;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class BillControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private BillsService billsService;

    @Autowired
    private JavaMonetaApiCurrencyExchange currencyExchange;

    void init(Currency currency, BigDecimal amount, boolean over, Account account) throws Exception {
        BillRequestDTO billRequestDTO = new BillRequestDTO(currency, over);

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills",account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(billRequestDTO)))
                .andExpect(status().isCreated());

        // Test fixture: seed the opening balance directly so the assertions below only see the transaction under test.
        Bill created = billsRepository.getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill -> bill.getCurrency().equals(currency) && bill.getIsOverdraft().equals(over) && bill.getAmount().signum() == 0)
                .findFirst().get();
        created.setAmount(amount);
        billsRepository.save(created);
    }

    Account initOtherAccount(){
        Account account = new Account("other", LocalDate.now(), UUID.randomUUID());
        accountsService.save(account);
        return accountsService.getAccountsByPersonId(account.getPersonId()).stream().findFirst().get();
    }

    Account initAccount(){
        UUID personId = UUID.randomUUID();
        Account account = new Account("account", LocalDate.now(), personId);
        accountsService.save(account);
        return accountsService.getAccounts().stream().findFirst().get();
    }

    @Test
    void save() throws Exception {

        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        boolean overdraft = true;

        Account account = initAccount();
        init(currency,amount,overdraft,account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        assertThat(bill).isNotNull();

        accountsService.delete(account.getId());
    }

    @Test
    void save_rejectsClientSuppliedBalance() throws Exception {
        Account account = initAccount();
        String request = "{\"currency\": \"USD\", \"isOverdraft\": false, \"amount\": 1000000}";

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest());

        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).isEmpty();
        accountsService.delete(account.getId());
    }

    @Test
    void save_createsZeroBalanceBillOnPathAccount() throws Exception {
        Account account = initAccount();

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(Currency.getInstance("USD"), false))))
                .andExpect(status().isCreated());

        Bill bill = billsRepository.getBillsByAccount_Id(account.getId()).stream().findFirst().get();
        assertThat(bill.getAmount().signum()).isEqualTo(0);
        assertThat(bill.getAccount().getId()).isEqualTo(account.getId());
        accountsService.delete(account.getId());
    }

    @Test
    void save_rejectsAccountOfAnotherPerson() throws Exception {
        Account account = initAccount();

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", UUID.randomUUID(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(Currency.getInstance("USD"), false))))
                .andExpect(status().isNotFound());

        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).isEmpty();
        accountsService.delete(account.getId());
    }

    @Test
    void update() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = BigDecimal.ZERO;
        boolean overdraft = true;
        Account account = initAccount();
        init(currency, amount, overdraft, account);

        Bill bill = billsRepository.getBillsByAccount_Id(account.getId()).stream().findFirst().get();

        Currency newCurrency = Currency.getInstance("RUB");
        boolean newOverdraft = false;
        BillRequestDTO billRequestDTO = new BillRequestDTO(newCurrency, newOverdraft);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}",account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(billRequestDTO)))
                .andExpect(status().isAccepted());

        Bill newBill = billsRepository.getBillById(bill.getId()).get();

        assertThat(newBill.getIsOverdraft()).isEqualTo(newOverdraft);
        assertThat(newBill.getCurrency()).isEqualTo(newCurrency);
        assertThat(newBill.getAmount().signum()).isEqualTo(0);
        assertThat(newBill.getAccount()).isEqualTo(account);
        accountsService.delete(account.getId());
    }

    @Test
    void update_rejectsBalanceAccountAndCurrencyChangesOutsideLedger() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        Account account = initAccount();
        Account otherAccount = initOtherAccount();
        init(currency, amount, true, account);

        Bill bill = billsRepository.getBillsByAccount_Id(account.getId()).stream().findFirst().get();
        String url = "/v1/people/{personId}/accounts/{accountId}/bills/{id}";

        mockMvc.perform(put(url, account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\": \"USD\", \"isOverdraft\": true, \"amount\": 1000000}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(url, account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\": \"USD\", \"isOverdraft\": true, \"account\": {\"id\": \"" + otherAccount.getId() + "\"}}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(url, otherAccount.getPersonId(), otherAccount.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(currency, true))))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(url, account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(Currency.getInstance("RUB"), true))))
                .andExpect(status().isConflict());

        Bill unchanged = billsRepository.getBillById(bill.getId()).get();
        assertThat(unchanged.getAmount().compareTo(amount)).isEqualTo(0);
        assertThat(unchanged.getCurrency()).isEqualTo(currency);
        assertThat(unchanged.getAccount()).isEqualTo(account);

        accountsService.delete(account.getId());
        accountsService.delete(otherAccount.getId());
    }

    @Test
    void commitAdjustment_ignoresClientSuppliedBillState() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        init(currency, BigDecimal.ZERO, false, account);

        Bill bill = billsRepository.getBillsByAccount_Id(account.getId()).stream().findFirst().get();
        Bill tampered = new Bill();
        tampered.setId(bill.getId());
        tampered.setCurrency(currency);
        tampered.setIsOverdraft(true);
        tampered.setAmount(new BigDecimal(1000000));
        tampered.setAccount(account);

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, BigDecimal.TEN, LocalDateTime.now(), "Opening balance", tampered);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());

        Bill updated = billsRepository.getBillById(bill.getId()).get();
        assertThat(updated.getAmount().compareTo(BigDecimal.TEN)).isEqualTo(0);
        assertThat(updated.getIsOverdraft()).isFalse();
        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).hasSize(1);
        accountsService.delete(account.getId());
    }

    @Test
    void delete() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        boolean overdraft = true;
        Account account = initAccount();
        init(currency,amount,overdraft,account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}",account.getPersonId(), account.getId(), bill.getId()))
                .andExpect(status().isNoContent());

        assertThat(billsRepository.getBillById(bill.getId()).isPresent()).isFalse();
    }

    @Test
    void commitPayment() throws Exception {

        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        BigDecimal paymentAmount = new BigDecimal(5,new MathContext(256, RoundingMode.HALF_EVEN));
        LocalDateTime time = LocalDateTime.now();
        boolean overdraft = true;
        Account account = initAccount();
        init(currency,amount,overdraft,account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        PaymentRequestDTO requestDTO = new PaymentRequestDTO(currency, paymentAmount, time,"Payment for order #1", bill);

        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments",account.getPersonId(), account.getId(), bill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());
        MvcResult mvcResult = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        Bill bill1 = billsRepository.getBillById(bill.getId()).get();
        Payment payment = billsService.getPaymentsByBillId(bill.getId()).stream().findFirst().get();
        FinancialTransactionResponseDTO financialTransaction = objectMapper
                .readValue(mvcResult.getResponse().getContentAsString(),new TypeReference<List<FinancialTransactionResponseDTO>>() {})
                .stream()
                .findFirst()
                .get();

        BigDecimal subtract = bill.getAmount().subtract(currencyExchange.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));
        BigDecimal billAmount = bill1.getAmount();

        assertThat(bill1.getAccount()).isEqualTo(account);
        assertThat(billAmount.compareTo(subtract)).isEqualTo(0);
        assertThat(bill1.getCurrency()).isEqualTo(currency);
        assertThat(bill1.getIsOverdraft()).isEqualTo(overdraft);

        assertThat(financialTransaction.getCurrency()).isEqualTo(currency);
        assertThat(financialTransaction.getAmount().compareTo(paymentAmount.negate())).isEqualTo(0);
        assertThat(financialTransaction.getMessage()).isEqualToIgnoringCase("Payment for order #1");

        accountsService.delete(account.getId());
    }

    @Test
    void commitPayment_withOverdraft() throws Exception {

        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        BigDecimal paymentAmount = new BigDecimal(15,new MathContext(256, RoundingMode.HALF_EVEN));
        LocalDateTime time = LocalDateTime.now();
        boolean overdraft = false;
        Account account = initAccount();
        init(currency,amount,overdraft,account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        PaymentRequestDTO requestDTO = new PaymentRequestDTO(currency, paymentAmount, time,"Payment for order #1", bill);

        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments",account.getPersonId(), account.getId(), bill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());

    }


    @Test
    void commitAdjustment() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        BigDecimal adjustmentAmount = new BigDecimal(5,new MathContext(256, RoundingMode.HALF_EVEN));
        LocalDateTime time = LocalDateTime.now();
        boolean overdraft = true;

        Account account = initAccount();
        init(currency,amount,overdraft,account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, adjustmentAmount, time,"Adjustment", bill);

        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments",account.getPersonId(), account.getId(), bill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());

        MvcResult mvcResult = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        Bill bill1 = billsRepository.getBillById(bill.getId()).get();
        Adjustment adjustment = billsService.getAdjustmentsByBillId(bill.getId()).stream().findFirst().get();
        FinancialTransactionResponseDTO financialTransaction = objectMapper
                .readValue(mvcResult.getResponse().getContentAsString(),new TypeReference<List<FinancialTransactionResponseDTO>>() {})
                .stream()
                .findFirst()
                .get();

        BigDecimal sum = bill.getAmount().add(currencyExchange.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));
        BigDecimal billAmount = bill1.getAmount();

        assertThat(bill1.getAccount()).isEqualTo(account);
        assertThat(billAmount.compareTo(sum)).isEqualTo(0);
        assertThat(bill1.getCurrency()).isEqualTo(currency);
        assertThat(bill1.getIsOverdraft()).isEqualTo(overdraft);

        assertThat(financialTransaction.getCurrency()).isEqualTo(currency);
        assertThat(financialTransaction.getAmount().compareTo(adjustmentAmount)).isEqualTo(0);
        assertThat(financialTransaction.getMessage()).isEqualToIgnoringCase("Adjustment");
        accountsService.delete(account.getId());
    }

    @Test
    void commitTransfer() throws Exception {

        Currency sourceBIllCurrency = Currency.getInstance("USD");
        Currency targetBIllCurrency = Currency.getInstance("RUB");
        Currency transferBIllCurrency = Currency.getInstance("EUR");
        BigDecimal sourceBillAmount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        BigDecimal targetBillAmount = new BigDecimal(0,new MathContext(256, RoundingMode.HALF_EVEN));
        BigDecimal transferAmount = new BigDecimal(5,new MathContext(256, RoundingMode.HALF_EVEN));
        boolean sourceBillOverdraft = false;
        boolean targetBillOverdraft = false;

        Account account = initAccount();
        init(sourceBIllCurrency,sourceBillAmount,sourceBillOverdraft,account);

        Bill sourceBill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(sourceBIllCurrency) && bill1.getIsOverdraft().equals(sourceBillOverdraft) && bill1.getAmount().compareTo(sourceBillAmount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        init(targetBIllCurrency,targetBillAmount,targetBillOverdraft,account);

        Bill targetBill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(targetBIllCurrency) && bill1.getIsOverdraft().equals(targetBillOverdraft) && bill1.getAmount().compareTo(targetBillAmount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        TransferRequestDTO transferRequestDTO = new TransferRequestDTO(transferBIllCurrency,transferAmount, LocalDateTime.now(), "Transfer", sourceBill, targetBill);

        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", account.getPersonId(), account.getId(), sourceBill.getId(), targetBill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequestDTO)))
                .andExpect(status().isAccepted());

        MvcResult mvcResultSource = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), sourceBill.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        MvcResult mvcResultTarget = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), targetBill.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        FinancialTransactionResponseDTO sourceFinancialTransaction = objectMapper
                .readValue(mvcResultSource.getResponse().getContentAsString(),new TypeReference<List<FinancialTransactionResponseDTO>>() {})
                .stream()
                .findFirst()
                .get();

        FinancialTransactionResponseDTO targetFinancialTransaction = objectMapper
                .readValue(mvcResultTarget.getResponse().getContentAsString(),new TypeReference<List<FinancialTransactionResponseDTO>>() {})
                .stream()
                .findFirst()
                .get();

        Bill newSourceBill = billsRepository.getBillById(sourceBill.getId()).get();
        Bill newTargetBill = billsRepository.getBillById(targetBill.getId()).get();

        Transfer sourceTransfer = billsService.getTransfersBySourceBillId(newSourceBill.getId()).stream().findFirst().get();
        Transfer targetTransfer = billsService.getTransfersByBeneficiaryBillId(newTargetBill.getId()).stream().findFirst().get();

        BigDecimal newSourceBillAmount = sourceBill.getAmount().subtract(currencyExchange.exchange(transferRequestDTO.getCurrency(), sourceBill.getCurrency(),transferRequestDTO.getAmount()));
        BigDecimal newTargetBillAmount = targetBill.getAmount().add(currencyExchange.exchange(transferRequestDTO.getCurrency(), targetBill.getCurrency(),transferRequestDTO.getAmount()));

        assertThat(newSourceBill.getAccount()).isEqualTo(account);
        assertThat(newSourceBill.getAmount().compareTo(newSourceBillAmount)).isEqualTo(0);
        assertThat(newSourceBill.getCurrency()).isEqualTo(sourceBIllCurrency);
        assertThat(newSourceBill.getIsOverdraft()).isEqualTo(sourceBillOverdraft);

        assertThat(newTargetBill.getAccount()).isEqualTo(account);
        assertThat(newTargetBill.getAmount().compareTo(newTargetBillAmount)).isEqualTo(0);
        assertThat(newTargetBill.getCurrency()).isEqualTo(targetBIllCurrency);
        assertThat(newTargetBill.getIsOverdraft()).isEqualTo(targetBillOverdraft);

        assertThat(sourceFinancialTransaction.getCurrency()).isEqualTo(transferBIllCurrency);
        assertThat(sourceFinancialTransaction.getAmount().compareTo(transferAmount.negate())).isEqualTo(0);
        assertThat(sourceFinancialTransaction.getMessage()).isEqualToIgnoringCase("Transfer");

        assertThat(targetFinancialTransaction.getCurrency()).isEqualTo(transferBIllCurrency);
        assertThat(targetFinancialTransaction.getAmount().compareTo(transferAmount)).isEqualTo(0);
        assertThat(targetFinancialTransaction.getMessage()).isEqualToIgnoringCase("Transfer");

        assertThat(sourceTransfer.getSourceBill()).isEqualTo(newSourceBill);
        assertThat(sourceTransfer.getBeneficiaryBill()).isEqualTo(newTargetBill);

        assertThat(targetTransfer.getSourceBill()).isEqualTo(newSourceBill);
        assertThat(targetTransfer.getBeneficiaryBill()).isEqualTo(newTargetBill);

        accountsService.delete(account.getId());
    }
}
