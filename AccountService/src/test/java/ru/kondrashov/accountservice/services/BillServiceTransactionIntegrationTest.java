package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.controllers.AccountsController;
import ru.kondrashov.accountservice.controllers.BillsController;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class BillServiceTransactionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Autowired
    private BillsService billsService;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private AccountsRepository accountsRepository;

    @MockBean
    private FinancialCurrencyExchange currencyExchange;

    @SpyBean
    private TransferService transferService;

    @SpyBean
    private AdjustmentsService adjustmentsService;

    private Account account;
    private Bill source;
    private Bill beneficiary;

    @BeforeEach
    void setUp() {
        when(currencyExchange.exchange(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        account = accountsRepository.save(new Account("account", LocalDate.now(), UUID.randomUUID()));
        source = billsRepository.save(new Bill(USD, new BigDecimal("100.00"), false, account));
        beneficiary = billsRepository.save(new Bill(EUR, new BigDecimal("10.00"), false, account));
    }

    @AfterEach
    void tearDown() {
        accountsRepository.deleteById(account.getId());
    }

    @Test
    void commitTransferMovesMoneyAndRecordsTransfer() {
        billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer("30.00"));

        assertThat(amountOf(source)).isEqualByComparingTo("70.00");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("40.00");
        assertThat(transferService.getTransfersBySourceBill_Id(source.getId())).hasSize(1);
    }

    @Test
    void commitTransferRollsBackDebitWhenBeneficiaryExchangeFails() {
        when(currencyExchange.exchange(eq(USD), eq(EUR), any())).thenThrow(new IllegalStateException("rate unavailable"));

        assertThatThrownBy(() -> billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer("30.00")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(source)).isEqualByComparingTo("100.00");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("10.00");
        assertThat(transferService.getTransfersBySourceBill_Id(source.getId())).isEmpty();
    }

    @Test
    void commitTransferRollsBackBalancesWhenTransferRecordFails() {
        doThrow(new IllegalStateException("insert failed")).when(transferService).save(any());

        assertThatThrownBy(() -> billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer("30.00")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(source)).isEqualByComparingTo("100.00");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("10.00");
    }

    @Test
    void commitAdjustmentRollsBackBalanceWhenAdjustmentRecordFails() {
        doThrow(new IllegalStateException("insert failed")).when(adjustmentsService).save(any());
        Adjustment adjustment = new Adjustment(USD, new BigDecimal("25.00"), LocalDateTime.now(), "correction", source);

        assertThatThrownBy(() -> billsService.commitAdjustment(source.getId(), adjustment))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(source)).isEqualByComparingTo("100.00");
        assertThat(adjustmentsService.getAdjustmentsByBillId(source.getId())).isEmpty();
    }

    @Test
    void controllersDoNotDeclareTransactions() {
        for (Class<?> controller : Arrays.asList(BillsController.class, AccountsController.class)) {
            assertThat(controller.isAnnotationPresent(Transactional.class)).as(controller.getSimpleName()).isFalse();
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(method.isAnnotationPresent(Transactional.class))
                        .as(controller.getSimpleName() + "." + method.getName())
                        .isFalse();
            }
        }
    }

    private Transfer transfer(String amount) {
        return new Transfer(USD, new BigDecimal(amount), LocalDateTime.now(), "transfer", source, beneficiary);
    }

    private BigDecimal amountOf(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }
}
