package ru.kondrashov.accountservice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class BillTransactionAtomicityIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private BillsService billsService;

    @Autowired
    private BillsRepository billsRepository;

    @Autowired
    private AccountsService accountsService;

    @SpyBean
    private TransferService transferService;

    @SpyBean
    private AdjustmentsService adjustmentsService;

    private final List<UUID> accounts = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        reset(transferService, adjustmentsService);
        accounts.forEach(accountsService::delete);
        accounts.clear();
    }

    private Bill newBill(BigDecimal amount, boolean overdraft) {
        Account account = new Account("account", LocalDate.now(), UUID.randomUUID());
        accountsService.save(account);
        Account saved = accountsService.getAccountsByPersonId(account.getPersonId()).stream().findFirst().get();
        accounts.add(saved.getId());
        return billsRepository.save(new Bill(USD, amount, overdraft, saved));
    }

    private BigDecimal balance(Bill bill) {
        return billsRepository.getBillById(bill.getId()).get().getAmount();
    }

    private Transfer transfer(BigDecimal amount) {
        return new Transfer(USD, amount, LocalDateTime.now(), "Transfer", null, null);
    }

    @Test
    void commitTransfer_rollsBackBothBalancesWhenLedgerWriteFails() {
        Bill source = newBill(BigDecimal.TEN, false);
        Bill beneficiary = newBill(BigDecimal.ZERO, false);
        doThrow(new IllegalStateException("ledger write failed")).when(transferService).save(any());

        assertThrows(IllegalStateException.class,
                () -> billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer(new BigDecimal(4))));

        assertThat(balance(source)).isEqualByComparingTo("10");
        assertThat(balance(beneficiary)).isEqualByComparingTo("0");
        assertThat(billsService.getTransfersBySourceBillId(source.getId())).isEmpty();
    }

    @Test
    void commitTransfer_rollsBackDebitWhenBeneficiaryDoesNotExist() {
        Bill source = newBill(BigDecimal.TEN, false);

        assertThrows(RuntimeException.class,
                () -> billsService.commitTransfer(source.getId(), UUID.randomUUID(), transfer(new BigDecimal(4))));

        assertThat(balance(source)).isEqualByComparingTo("10");
    }

    @Test
    void commitTransfer_recordsPathBillsEvenWhenBodyNamesOtherBills() {
        Bill source = newBill(BigDecimal.TEN, false);
        Bill beneficiary = newBill(BigDecimal.ZERO, false);
        Bill unrelated = newBill(BigDecimal.ZERO, false);
        Transfer transfer = new Transfer(USD, new BigDecimal(4), LocalDateTime.now(), "Transfer", unrelated, unrelated);

        billsService.commitTransfer(source.getId(), beneficiary.getId(), transfer);

        Transfer recorded = billsService.getTransfersBySourceBillId(source.getId()).stream().findFirst().get();
        assertThat(recorded.getSourceBill().getId()).isEqualTo(source.getId());
        assertThat(recorded.getBeneficiaryBill().getId()).isEqualTo(beneficiary.getId());
        assertThat(billsService.getTransfersBySourceBillId(unrelated.getId())).isEmpty();
        assertThat(balance(source)).isEqualByComparingTo("6");
        assertThat(balance(beneficiary)).isEqualByComparingTo("4");
        assertThat(balance(unrelated)).isEqualByComparingTo("0");
    }

    @Test
    void commitAdjustment_rollsBackBalanceWhenLedgerWriteFails() {
        Bill bill = newBill(BigDecimal.TEN, false);
        doThrow(new IllegalStateException("ledger write failed")).when(adjustmentsService).save(any());

        assertThrows(IllegalStateException.class, () -> billsService.commitAdjustment(bill.getId(),
                new Adjustment(USD, new BigDecimal(5), LocalDateTime.now(), "Adjustment", null)));

        assertThat(balance(bill)).isEqualByComparingTo("10");
        assertThat(billsService.getAdjustmentsByBillId(bill.getId())).isEmpty();
    }

    @Test
    void concurrentTransfersCannotOverdrawANonOverdraftBill() throws Exception {
        Bill source = newBill(BigDecimal.TEN, false);
        Bill beneficiaryA = newBill(BigDecimal.ZERO, false);
        Bill beneficiaryB = newBill(BigDecimal.ZERO, false);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> toA = () -> {
            start.await();
            billsService.commitTransfer(source.getId(), beneficiaryA.getId(), transfer(BigDecimal.TEN));
            return true;
        };
        Callable<Boolean> toB = () -> {
            start.await();
            billsService.commitTransfer(source.getId(), beneficiaryB.getId(), transfer(BigDecimal.TEN));
            return true;
        };
        Future<Boolean> a = pool.submit(toA);
        Future<Boolean> b = pool.submit(toB);

        int succeeded = 0;
        int rejected = 0;
        for (Future<Boolean> f : new Future[]{a, b}) {
            try {
                f.get(30, TimeUnit.SECONDS);
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(NotEnoughMoneyException.class);
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        assertThat(balance(source)).isEqualByComparingTo("0");
        assertThat(balance(beneficiaryA).add(balance(beneficiaryB))).isEqualByComparingTo("10");
    }
}
