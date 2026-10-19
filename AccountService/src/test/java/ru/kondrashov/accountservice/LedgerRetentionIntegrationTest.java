package ru.kondrashov.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.*;
import ru.kondrashov.accountservice.services.LedgerRetentionService;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class LedgerRetentionIntegrationTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountsRepository accountsRepository;
    @Autowired private BillsRepository billsRepository;
    @Autowired private AdjustmentsRepository adjustmentsRepository;
    @Autowired private PaymentsRepository paymentsRepository;
    @Autowired private TransferRepository transferRepository;
    @Autowired private LedgerPurgeRecordRepository purgeRecordRepository;
    @Autowired private AccountsService accountsService;
    @Autowired private BillsService billsService;
    @Autowired private LedgerRetentionService retentionService;
    @Autowired private PlatformTransactionManager transactionManager;

    private Account account() {
        return accountsRepository.save(new Account("account", LocalDate.now(), UUID.randomUUID()));
    }

    private Bill bill(Account account) {
        return billsRepository.save(new Bill(USD, BigDecimal.TEN, true, account));
    }

    private void backdateClose(Bill bill, LocalDateTime closedAt) {
        Bill stored = billsRepository.getBillById(bill.getId()).get();
        stored.setClosedAt(closedAt);
        billsRepository.save(stored);
    }

    @Test
    void ledgerForeignKeysDoNotCascadeDeletes() {
        List<String> rules = jdbcTemplate.queryForList(
                "SELECT tc.table_name || '.' || kcu.column_name || '=' || rc.delete_rule " +
                "FROM information_schema.referential_constraints rc " +
                "JOIN information_schema.table_constraints tc ON tc.constraint_name = rc.constraint_name " +
                "JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = rc.constraint_name " +
                "WHERE tc.table_name IN ('adjustments', 'payments', 'transfers', 'bills')",
                String.class);

        assertThat(rules).containsExactlyInAnyOrder(
                "adjustments.bill_id=NO ACTION",
                "payments.bill_id=NO ACTION",
                "transfers.source_bill_id=NO ACTION",
                "transfers.beneficiary_bill_id=NO ACTION",
                "bills.account_id=NO ACTION");
    }

    @Test
    void hardDeletingABillWithAdjustmentsIsRejectedByTheDatabase() {
        Bill bill = bill(account());
        billsService.commitAdjustment(bill.getId(), new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "correction", bill));

        assertThatThrownBy(() -> billsRepository.deleteById(bill.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(bill.getId())).hasSize(1);
    }

    @Test
    void closingAnAccountClosesItsBillsAndKeepsTheirLedger() {
        Account account = account();
        Bill bill = bill(account);
        billsService.commitAdjustment(bill.getId(), new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "correction", bill));
        billsService.commitPayment(bill.getId(), new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", bill));

        accountsService.delete(account.getId());

        assertThat(accountsRepository.getAccountsById(account.getId()).get().getClosedAt()).isNotNull();
        assertThat(billsRepository.getBillById(bill.getId()).get().getClosedAt()).isNotNull();
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(bill.getId())).hasSize(1);
        assertThat(paymentsRepository.getPaymentsByBill_Id(bill.getId())).hasSize(1);
        assertThat(retentionService.purgeClosedBefore(LocalDateTime.now().minusYears(7))).isZero();
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(bill.getId())).hasSize(1);
    }

    @Test
    void purgeDeletesExpiredLedgerOnlyAfterWritingAnAuditRecord() {
        Account account = account();
        Bill expired = bill(account);
        Bill expiredCounterparty = bill(account);
        Bill retainedCounterparty = bill(account);
        Bill heldBack = bill(account);

        billsService.commitAdjustment(expired.getId(), new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "correction", expired));
        billsService.commitPayment(expired.getId(), new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", expired));
        billsService.commitTransfer(expired.getId(), expiredCounterparty.getId(),
                new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "transfer", expired, expiredCounterparty));
        billsService.commitAdjustment(heldBack.getId(), new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "correction", heldBack));
        billsService.commitTransfer(heldBack.getId(), retainedCounterparty.getId(),
                new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "transfer", heldBack, retainedCounterparty));

        LocalDateTime longAgo = LocalDateTime.now().minusYears(8);
        backdateClose(expired, longAgo);
        backdateClose(expiredCounterparty, longAgo);
        backdateClose(heldBack, longAgo);

        retentionService.purgeClosedBefore(LocalDateTime.now().minusYears(7));

        assertThat(billsRepository.getBillById(expired.getId())).isEmpty();
        assertThat(billsRepository.getBillById(expiredCounterparty.getId())).isEmpty();
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(expired.getId())).isEmpty();
        assertThat(paymentsRepository.getPaymentsByBill_Id(expired.getId())).isEmpty();
        assertThat(transferRepository.getTransfersBySourceBill_Id(expired.getId())).isEmpty();

        LedgerPurgeRecord record = purgeRecordRepository.getLedgerPurgeRecordsByBillId(expired.getId()).iterator().next();
        assertThat(record.getAccountId()).isEqualTo(account.getId());
        assertThat(record.getAdjustmentsPurged()).isEqualTo(1);
        assertThat(record.getPaymentsPurged()).isEqualTo(1);
        LedgerPurgeRecord counterpartyRecord = purgeRecordRepository.getLedgerPurgeRecordsByBillId(expiredCounterparty.getId()).iterator().next();
        // The shared transfer is audited by whichever of the two bills is purged first.
        assertThat(record.getTransfersPurged() + counterpartyRecord.getTransfersPurged()).isEqualTo(1);

        // heldBack is past retention, but its transfer is still part of an open bill's history.
        assertThat(billsRepository.getBillById(heldBack.getId())).isPresent();
        assertThat(adjustmentsRepository.getAdjustmentsByBill_Id(heldBack.getId())).hasSize(1);
        assertThat(transferRepository.getTransfersByBeneficiaryBill_Id(retainedCounterparty.getId())).hasSize(1);
        assertThat(purgeRecordRepository.getLedgerPurgeRecordsByBillId(heldBack.getId())).isEmpty();
    }

    @Test
    void closedAccountRejectsNewBillsAndStaleBillUpdates() {
        Account closed = account();
        Account open = account();
        Bill openBill = bill(open);
        Account staleCopy = accountsRepository.getAccountsById(closed.getId()).get();
        accountsService.delete(closed.getId());

        assertThatThrownBy(() -> billsService.save(new Bill(USD, BigDecimal.TEN, true, staleCopy)))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(billsRepository.getBillsByAccount_Id(closed.getId())).isEmpty();

        assertThatThrownBy(() -> billsService.update(openBill.getId(), new Bill(USD, BigDecimal.TEN, true, staleCopy)))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(accountsRepository.getAccountsById(closed.getId()).get().getClosedAt()).isNotNull();
        assertThat(billsRepository.getBillById(openBill.getId()).get().getAccount().getId()).isEqualTo(open.getId());
    }

    @Test
    void closingABillWaitsForAnInFlightPostingAndThenBlocksNewOnes() throws Exception {
        Bill bill = bill(account());

        TransactionStatus posting = transactionManager.getTransaction(new DefaultTransactionDefinition());
        CompletableFuture<Void> close;
        try {
            Bill locked = billsRepository.lockActiveBill(bill.getId()).get();
            close = CompletableFuture.runAsync(() -> billsService.delete(bill.getId()));
            Thread.sleep(500);
            assertThat(close).isNotDone();
            locked.setAmount(locked.getAmount().add(BigDecimal.ONE));
            billsRepository.save(locked);
        } finally {
            transactionManager.commit(posting);
        }
        close.get(10, TimeUnit.SECONDS);

        Bill stored = billsRepository.getBillById(bill.getId()).get();
        assertThat(stored.getClosedAt()).isNotNull();
        assertThat(stored.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(11));
        assertThatThrownBy(() -> billsService.commitPayment(bill.getId(), new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "late", bill)))
                .isInstanceOf(BillNotFoundException.class);
    }
}
