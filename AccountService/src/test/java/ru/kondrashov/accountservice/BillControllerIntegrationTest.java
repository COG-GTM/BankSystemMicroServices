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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        BillRequestDTO billRequestDTO = new BillRequestDTO(currency, amount , over, account);

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills",account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(billRequestDTO)))
                .andExpect(status().isCreated());
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
    void update() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        boolean overdraft = true;
        Account account = initAccount();
        init(currency, amount, overdraft, account);

        Bill bill = billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(overdraft) && bill1.getAmount().compareTo(amount)==0 && bill1.getAccount().equals(account))
                .findFirst().get();

        Currency newCurrency = Currency.getInstance("RUB");
        boolean newOverdraft = false;
        BillRequestDTO billRequestDTO = new BillRequestDTO(newCurrency, amount , newOverdraft, account);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}",account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(billRequestDTO)))
                .andExpect(status().isAccepted());

        Bill newBill = billsRepository.getBillById(bill.getId()).get();
        BigDecimal newAmount = currencyExchange.exchange(currency,newCurrency,amount);

        assertThat(newBill.getIsOverdraft()).isEqualTo(newOverdraft);
        assertThat(newBill.getCurrency()).isEqualTo(newCurrency);
        assertThat(newBill.getAmount().compareTo(newAmount)).isEqualTo(0);
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

        Bill closed = billsRepository.getBillById(bill.getId()).get();
        assertThat(closed.getStatus()).isEqualTo(RecordStatus.CLOSED);
        assertThat(closed.getClosedAt()).isNotNull();
        assertThat(billsService.getBillsByAccountId(account.getId())).isEmpty();
    }

    @Test
    void closingBillKeepsTransactionHistoryOfBothSides() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        Account account = initAccount();
        init(currency, amount, false, account);
        init(currency, BigDecimal.ZERO, true, account);
        Bill source = findBill(account, false);
        Bill beneficiary = findBill(account, true);

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", account.getPersonId(), account.getId(), source.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequestDTO(currency, BigDecimal.ONE, LocalDateTime.now(), "Payment", source))))
                .andExpect(status().isAccepted());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{id}/adjustments", account.getPersonId(), account.getId(), source.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdjustmentRequestDTO(currency, BigDecimal.ONE, LocalDateTime.now(), "Adjustment", source))))
                .andExpect(status().isAccepted());
        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", account.getPersonId(), account.getId(), source.getId(), beneficiary.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TransferRequestDTO(currency, BigDecimal.ONE, LocalDateTime.now(), "Transfer", source, beneficiary))))
                .andExpect(status().isAccepted());

        mockMvc.perform(MockMvcRequestBuilders.delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", account.getPersonId(), account.getId(), source.getId()))
                .andExpect(status().isNoContent());

        assertThat(billsService.getPaymentsByBillId(source.getId())).hasSize(1);
        assertThat(billsService.getAdjustmentsByBillId(source.getId())).hasSize(1);
        assertThat(billsService.getTransfersBySourceBillId(source.getId())).hasSize(1);
        assertThat(billsService.getTransfersByBeneficiaryBillId(beneficiary.getId())).hasSize(1);
        assertThat(transactionsOf(account, source)).hasSize(3);
        assertThat(transactionsOf(account, beneficiary)).hasSize(1);

        accountsService.delete(account.getId());

        assertThat(billsRepository.getBillById(beneficiary.getId()).get().getStatus()).isEqualTo(RecordStatus.CLOSED);
        assertThat(transactionsOf(account, source)).hasSize(3);
        assertThat(transactionsOf(account, beneficiary)).hasSize(1);
    }

    @Test
    void closedBillRejectsNewTransactions() throws Exception {
        Currency currency = Currency.getInstance("USD");
        BigDecimal amount = new BigDecimal(10,new MathContext(256, RoundingMode.HALF_EVEN));
        Account account = initAccount();
        init(currency, amount, true, account);
        Bill bill = findBill(account, true);

        billsService.delete(bill.getId());

        mockMvc.perform(put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PaymentRequestDTO(currency, BigDecimal.ONE, LocalDateTime.now(), "Payment", bill))))
                .andExpect(status().isConflict());
        assertThat(billsService.getPaymentsByBillId(bill.getId())).isEmpty();
        assertThat(billsRepository.getBillById(bill.getId()).get().getAmount().compareTo(amount)).isEqualTo(0);

        accountsService.delete(account.getId());
    }

    @Test
    void closedAccountRejectsNewBills() throws Exception {
        Account account = initAccount();
        accountsService.delete(account.getId());

        mockMvc.perform(post("/v1/people/{personId}/accounts/{accountId}/bills", account.getPersonId(), account.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.ZERO, true, account))))
                .andExpect(status().isConflict());
        assertThat(billsRepository.getBillsByAccount_Id(account.getId())).isEmpty();
    }

    @Test
    void hardDeleteOfBillWithTransactionsIsRejectedByDatabase() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        init(currency, BigDecimal.TEN, true, account);
        Bill bill = findBill(account, true);
        billsService.commitPayment(bill.getId(), new Payment(currency, BigDecimal.ONE, LocalDateTime.now(), "Payment", bill));

        assertThatThrownBy(() -> billsRepository.delete(billsRepository.getBillById(bill.getId()).get()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(billsService.getPaymentsByBillId(bill.getId())).hasSize(1);

        accountsService.delete(account.getId());
    }

    private Bill findBill(Account account, boolean overdraft) {
        return billsRepository.getBillsByAccount_Id(account.getId())
                .stream()
                .filter(b -> b.getIsOverdraft().equals(overdraft))
                .findFirst().get();
    }

    private List<FinancialTransactionResponseDTO> transactionsOf(Account account, Bill bill) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<FinancialTransactionResponseDTO>>() {});
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
