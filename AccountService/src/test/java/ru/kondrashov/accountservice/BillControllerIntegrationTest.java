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
import ru.kondrashov.accountservice.audit.AuditContextResolver;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.AuditOutcome;
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

    private static final String MAKER = "maker-1";
    private static final String CHECKER = "checker-1";

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

    Bill initBill(Currency currency, BigDecimal amount, boolean over, Account account) throws Exception {
        init(currency, amount, over, account);
        return billsRepository.
                getBillsByAccount_Id(account.getId())
                .stream()
                .filter(bill1 -> bill1.getCurrency().equals(currency) && bill1.getIsOverdraft().equals(over) && bill1.getAmount().compareTo(amount)==0)
                .findFirst().get();
    }

    List<AuditEventResponseDTO> auditEvents(Account account, UUID billId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/audit-events", account.getPersonId(), account.getId(), billId))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<List<AuditEventResponseDTO>>() {});
    }

    AuditEventResponseDTO lastAuditEvent(Account account, UUID billId, AuditOperation operation) throws Exception {
        List<AuditEventResponseDTO> events = auditEvents(account, billId);
        return events.stream()
                .filter(event -> event.getOperation() == operation)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No " + operation + " audit event in " + events));
    }

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
                .header(AuditContextResolver.ACTOR_HEADER, MAKER)
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
                .delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}",account.getPersonId(), account.getId(), bill.getId())
                .header(AuditContextResolver.ACTOR_HEADER, MAKER))
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
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
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
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
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

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, adjustmentAmount, time,"Adjustment", bill, CHECKER);

        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments",account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
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
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
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

    @Test
    void commitPayment_writesSuccessAuditEventAndInitiator() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        PaymentRequestDTO requestDTO = new PaymentRequestDTO(currency, new BigDecimal("4"), LocalDateTime.now(), "Payment for order #2", bill);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .header(AuditContextResolver.REQUEST_ID_HEADER, "req-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.PAYMENT);
        Payment payment = billsService.getPaymentsByBillId(bill.getId()).stream().findFirst().get();

        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActor()).isEqualTo(MAKER);
        assertThat(event.getPersonId()).isEqualTo(account.getPersonId());
        assertThat(event.getAccountId()).isEqualTo(account.getId());
        assertThat(event.getBillId()).isEqualTo(bill.getId());
        assertThat(event.getTransactionId()).isEqualTo(payment.getId());
        assertThat(event.getAmount()).isEqualByComparingTo("4");
        assertThat(event.getCurrency()).isEqualTo("USD");
        assertThat(event.getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getBalanceAfter()).isEqualByComparingTo("6");
        assertThat(event.getRequestId()).isEqualTo("req-123");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(payment.getInitiatedBy()).isEqualTo(MAKER);

        accountsService.delete(account.getId());
    }

    @Test
    void commitPayment_withoutActor_isRejectedAndAudited() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        PaymentRequestDTO requestDTO = new PaymentRequestDTO(currency, new BigDecimal("4"), LocalDateTime.now(), "Payment", bill);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", account.getPersonId(), account.getId(), bill.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.PAYMENT);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.getActor()).isEqualTo("unidentified");
        assertThat(event.getFailureReason()).contains("MissingActorException");
        assertThat(billsRepository.getBillById(bill.getId()).get().getAmount()).isEqualByComparingTo("10");
        assertThat(billsService.getPaymentsByBillId(bill.getId())).isEmpty();

        accountsService.delete(account.getId());
    }

    @Test
    void commitPayment_failure_isAuditedAndBalanceUnchanged() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), false, account);

        PaymentRequestDTO requestDTO = new PaymentRequestDTO(currency, new BigDecimal("15"), LocalDateTime.now(), "Payment", bill);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments", account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.PAYMENT);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILED);
        assertThat(event.getActor()).isEqualTo(MAKER);
        assertThat(event.getFailureReason()).contains("NotEnoughMoneyException");
        assertThat(event.getAmount()).isEqualByComparingTo("15");
        assertThat(event.getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getBalanceAfter()).isEqualByComparingTo("10");
        assertThat(billsRepository.getBillById(bill.getId()).get().getAmount()).isEqualByComparingTo("10");
        assertThat(billsService.getPaymentsByBillId(bill.getId())).isEmpty();

        accountsService.delete(account.getId());
    }

    @Test
    void commitAdjustment_recordsMakerAndChecker() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, new BigDecimal("3"), LocalDateTime.now(), "Correction", bill, CHECKER);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.ADJUSTMENT);
        Adjustment adjustment = billsService.getAdjustmentsByBillId(bill.getId()).stream().findFirst().get();

        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActor()).isEqualTo(MAKER);
        assertThat(event.getApprovedBy()).isEqualTo(CHECKER);
        assertThat(event.getTransactionId()).isEqualTo(adjustment.getId());
        assertThat(event.getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getBalanceAfter()).isEqualByComparingTo("13");
        assertThat(adjustment.getInitiatedBy()).isEqualTo(MAKER);
        assertThat(adjustment.getApprovedBy()).isEqualTo(CHECKER);

        accountsService.delete(account.getId());
    }

    @Test
    void commitAdjustment_selfApproved_isForbiddenAndAudited() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, new BigDecimal("3"), LocalDateTime.now(), "Correction", bill, MAKER);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isForbidden());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.ADJUSTMENT);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(event.getFailureReason()).contains("MakerCheckerViolationException");
        assertThat(billsRepository.getBillById(bill.getId()).get().getAmount()).isEqualByComparingTo("10");
        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).isEmpty();

        accountsService.delete(account.getId());
    }

    @Test
    void commitAdjustment_withoutApprover_returns400() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        AdjustmentRequestDTO requestDTO = new AdjustmentRequestDTO(currency, new BigDecimal("3"), LocalDateTime.now(), "Correction", bill, null);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/adjustments", account.getPersonId(), account.getId(), bill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());

        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).isEmpty();

        accountsService.delete(account.getId());
    }

    @Test
    void commitTransfer_auditsBothBills() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill sourceBill = initBill(currency, new BigDecimal("10"), false, account);
        Bill targetBill = initBill(currency, new BigDecimal("1"), false, account);

        TransferRequestDTO requestDTO = new TransferRequestDTO(currency, new BigDecimal("5"), LocalDateTime.now(), "Transfer", sourceBill, targetBill);
        mockMvc.perform(
                put("/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}", account.getPersonId(), account.getId(), sourceBill.getId(), targetBill.getId())
                        .header(AuditContextResolver.ACTOR_HEADER, MAKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isAccepted());

        AuditEventResponseDTO event = lastAuditEvent(account, sourceBill.getId(), AuditOperation.TRANSFER);
        Transfer transfer = billsService.getTransfersBySourceBillId(sourceBill.getId()).stream().findFirst().get();

        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActor()).isEqualTo(MAKER);
        assertThat(event.getBillId()).isEqualTo(sourceBill.getId());
        assertThat(event.getCounterpartyBillId()).isEqualTo(targetBill.getId());
        assertThat(event.getTransactionId()).isEqualTo(transfer.getId());
        assertThat(event.getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getBalanceAfter()).isEqualByComparingTo("5");
        assertThat(event.getCounterpartyBalanceBefore()).isEqualByComparingTo("1");
        assertThat(event.getCounterpartyBalanceAfter()).isEqualByComparingTo("6");
        assertThat(transfer.getInitiatedBy()).isEqualTo(MAKER);
        assertThat(lastAuditEvent(account, targetBill.getId(), AuditOperation.TRANSFER).getId()).isEqualTo(event.getId());

        accountsService.delete(account.getId());
    }

    @Test
    void delete_auditEventSurvivesBillDeletion() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        mockMvc.perform(MockMvcRequestBuilders
                .delete("/v1/people/{personId}/accounts/{accountId}/bills/{id}", account.getPersonId(), account.getId(), bill.getId())
                .header(AuditContextResolver.ACTOR_HEADER, MAKER))
                .andExpect(status().isNoContent());

        AuditEventResponseDTO event = lastAuditEvent(account, bill.getId(), AuditOperation.BILL_DELETED);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActor()).isEqualTo(MAKER);
        assertThat(event.getBalanceBefore()).isEqualByComparingTo("10");
        assertThat(event.getBalanceCurrency()).isEqualTo("USD");
        assertThat(event.getBalanceAfter()).isNull();
        assertThat(lastAuditEvent(account, bill.getId(), AuditOperation.BILL_CREATED).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/audit-events", account.getPersonId(), UUID.randomUUID(), bill.getId()))
                .andExpect(status().isNotFound());

        accountsService.delete(account.getId());
    }

    @Test
    void auditEvents_areNotReadableThroughAnotherPersonOrAccount() throws Exception {
        Currency currency = Currency.getInstance("USD");
        Account account = initAccount();
        Bill bill = initBill(currency, new BigDecimal("10"), true, account);

        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/audit-events", account.getPersonId(), UUID.randomUUID(), bill.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/audit-events", UUID.randomUUID(), account.getId(), bill.getId()))
                .andExpect(status().isNotFound());
        assertThat(auditEvents(account, bill.getId())).isNotEmpty();

        accountsService.delete(account.getId());
    }
}
