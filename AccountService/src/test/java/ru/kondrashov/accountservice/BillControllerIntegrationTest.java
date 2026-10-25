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
import ru.kondrashov.accountservice.controllers.BillsController;
import ru.kondrashov.accountservice.controllers.dto.*;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.AdjustmentsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.repositories.PaymentsRepository;
import ru.kondrashov.accountservice.repositories.TransferRepository;
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
import java.util.stream.Collectors;
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

    @Autowired
    private AdjustmentsRepository adjustmentsRepository;

    @Autowired
    private PaymentsRepository paymentsRepository;

    @Autowired
    private TransferRepository transferRepository;

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

    @Test
    void getFinancialTransactions_returnsSignedNewestFirstPages() throws Exception {
        Currency usd = Currency.getInstance("USD");
        Account account = initAccount();
        init(usd, new BigDecimal(1000), true, account);
        init(Currency.getInstance("EUR"), new BigDecimal(1000), true, account);
        Bill bill = billsRepository.getBillsByAccount_Id(account.getId()).stream()
                .filter(b -> b.getCurrency().equals(usd)).findFirst().get();
        Bill otherBill = billsRepository.getBillsByAccount_Id(account.getId()).stream()
                .filter(b -> !b.getCurrency().equals(usd)).findFirst().get();
        LocalDateTime t0 = LocalDateTime.of(2021, 3, 1, 10, 0);

        adjustmentsRepository.save(new Adjustment(usd, new BigDecimal("100.00"), t0, "adjustment", bill));
        paymentsRepository.save(new Payment(usd, new BigDecimal("10.00"), t0.plusMinutes(1), "payment", bill));
        transferRepository.save(new Transfer(usd, new BigDecimal("20.00"), t0.plusMinutes(2), "incoming", otherBill, bill));
        transferRepository.save(new Transfer(usd, new BigDecimal("30.00"), t0.plusMinutes(3), "outgoing", bill, otherBill));
        paymentsRepository.save(new Payment(usd, new BigDecimal("5.00"), t0.plusMinutes(4), "other bill payment", otherBill));

        MvcResult first = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId())
                .param("size", "3"))
                .andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId())
                .param("page", "1").param("size", "3"))
                .andExpect(status().isOk()).andReturn();
        MvcResult all = mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", account.getPersonId(), account.getId(), bill.getId()))
                .andExpect(status().isOk()).andReturn();

        List<FinancialTransactionResponseDTO> firstPage = readTransactions(first);
        List<FinancialTransactionResponseDTO> secondPage = readTransactions(second);

        assertThat(firstPage.stream().map(FinancialTransactionResponseDTO::getMessage).collect(Collectors.toList()))
                .containsExactly("outgoing", "incoming", "payment");
        assertThat(firstPage.stream().map(FinancialTransactionResponseDTO::getAmount).collect(Collectors.toList()))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-30"), new BigDecimal("20"), new BigDecimal("-10"));
        assertThat(first.getResponse().getHeader(BillsController.HAS_NEXT_PAGE_HEADER)).isEqualTo("true");

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getMessage()).isEqualTo("adjustment");
        assertThat(secondPage.get(0).getAmount().compareTo(new BigDecimal("100"))).isEqualTo(0);
        assertThat(secondPage.get(0).getCurrency()).isEqualTo(usd);
        assertThat(secondPage.get(0).getTime()).isEqualTo(t0);
        assertThat(second.getResponse().getHeader(BillsController.HAS_NEXT_PAGE_HEADER)).isEqualTo("false");

        assertThat(readTransactions(all)).hasSize(4);
        assertThat(all.getResponse().getHeader(BillsController.PAGE_SIZE_HEADER)).isEqualTo("100");

        accountsService.delete(account.getId());
    }

    @Test
    void getFinancialTransactions_rejectsInvalidPageRequests() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", id, id, id).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", id, id, id).param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{billId}/transactions", id, id, id).param("size", "501"))
                .andExpect(status().isBadRequest());
    }

    private List<FinancialTransactionResponseDTO> readTransactions(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<List<FinancialTransactionResponseDTO>>() {});
    }
}
