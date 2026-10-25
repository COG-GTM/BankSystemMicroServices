package ru.kondrashov.accountservice;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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

import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties", properties = "spring.jpa.properties.hibernate.generate_statistics=true")
public class BillMoneyMovementTransactionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private BillsService billsService;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private AccountsRepository accountsRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @SpyBean
    private TransferService transferService;

    @SpyBean
    private AdjustmentsService adjustmentsService;

    @MockBean
    private FinancialCurrencyExchange currencyExchange;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        given(currencyExchange.exchange(any(), any(), any())).willAnswer(invocation -> invocation.getArgument(2));
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void transferCommitsBothBillsAndTheTransferInOneTransaction() {
        Bill source = newBill("100.00", false);
        Bill beneficiary = newBill("5.00", false);

        statistics.clear();
        billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer(source, beneficiary, "40.00"));

        assertThat(statistics.getSuccessfulTransactionCount()).isEqualTo(1);
        assertThat(amountOf(source)).isEqualByComparingTo("60.00");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("45.00");
        assertThat(billsService.getTransfersBySourceBillId(source.getId())).hasSize(1);
    }

    @Test
    void adjustmentCommitsTheBillAndTheAdjustmentInOneTransaction() {
        Bill bill = newBill("10.00", false);

        statistics.clear();
        billsService.commitAdjustment(bill.getId(), adjustment(bill, "2.50"));

        assertThat(statistics.getSuccessfulTransactionCount()).isEqualTo(1);
        assertThat(amountOf(bill)).isEqualByComparingTo("12.50");
        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).hasSize(1);
    }

    @Test
    void transferIsRolledBackWhenTheTransferRecordCannotBeSaved() {
        Bill source = newBill("100.00", false);
        Bill beneficiary = newBill("5.00", false);
        doThrow(new IllegalStateException("transfer insert failed")).when(transferService).save(any());

        assertThatThrownBy(() -> billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer(source, beneficiary, "40.00")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(source)).isEqualByComparingTo("100.00");
        assertThat(amountOf(beneficiary)).isEqualByComparingTo("5.00");
    }

    @Test
    void adjustmentIsRolledBackWhenTheAdjustmentRecordCannotBeSaved() {
        Bill bill = newBill("10.00", false);
        doThrow(new IllegalStateException("adjustment insert failed")).when(adjustmentsService).save(any());

        assertThatThrownBy(() -> billsService.commitAdjustment(bill.getId(), adjustment(bill, "2.50")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(amountOf(bill)).isEqualByComparingTo("10.00");
    }

    @Test
    void concurrentOppositeTransfersNeitherLoseUpdatesNorDeadlock() throws Exception {
        Bill first = newBill("1000.00", false);
        Bill second = newBill("1000.00", false);
        int threads = 8;
        int transfersPerThread = 25;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            boolean forward = t % 2 == 0;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < transfersPerThread; i++) {
                    Bill from = forward ? first : second;
                    Bill to = forward ? second : first;
                    billsService.commitTransfer(from.getId(), to.getId(), transfer(from, to, "1.00"));
                }
                return null;
            }));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        for (Future<?> future : futures) {
            future.get();
        }

        assertThat(amountOf(first)).isEqualByComparingTo("1000.00");
        assertThat(amountOf(second)).isEqualByComparingTo("1000.00");
        assertThat(billsService.getTransfersBySourceBillId(first.getId())).hasSize(threads / 2 * transfersPerThread);
        assertThat(billsService.getTransfersBySourceBillId(second.getId())).hasSize(threads / 2 * transfersPerThread);
    }

    private Bill newBill(String amount, boolean overdraft) {
        Account account = accountsRepository.save(new Account("account", LocalDate.now(), UUID.randomUUID()));
        return billsRepository.save(new Bill(USD, new BigDecimal(amount), overdraft, account));
    }

    private BigDecimal amountOf(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }

    private Transfer transfer(Bill source, Bill beneficiary, String amount) {
        return new Transfer(USD, new BigDecimal(amount), LocalDateTime.now(), "Transfer", source, beneficiary);
    }

    private Adjustment adjustment(Bill bill, String amount) {
        return new Adjustment(USD, new BigDecimal(amount), LocalDateTime.now(), "Adjustment", bill);
    }
}
