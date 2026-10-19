package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.*;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

/**
 * The only code path that deletes ledger rows (adjustments, payments, transfers).
 * A closed bill's history is purged once it has been closed for longer than the
 * retention period, and every purge writes a {@link LedgerPurgeRecord}.
 */
@Service
public class LedgerRetentionService {

    private final BillsRepository billsRepository;
    private final AdjustmentsRepository adjustmentsRepository;
    private final PaymentsRepository paymentsRepository;
    private final TransferRepository transferRepository;
    private final LedgerPurgeRecordRepository purgeRecordRepository;
    private final Logger logger;
    private final int retentionYears;

    public LedgerRetentionService(BillsRepository billsRepository,
                                  AdjustmentsRepository adjustmentsRepository,
                                  PaymentsRepository paymentsRepository,
                                  TransferRepository transferRepository,
                                  LedgerPurgeRecordRepository purgeRecordRepository,
                                  Logger logger,
                                  @Value("${ledger.retention.years:7}") int retentionYears) {
        if (retentionYears < 1) {
            throw new IllegalArgumentException("ledger.retention.years must be at least 1");
        }
        this.billsRepository = billsRepository;
        this.adjustmentsRepository = adjustmentsRepository;
        this.paymentsRepository = paymentsRepository;
        this.transferRepository = transferRepository;
        this.purgeRecordRepository = purgeRecordRepository;
        this.logger = logger;
        this.retentionYears = retentionYears;
    }

    @Scheduled(cron = "${ledger.retention.purge-cron:0 0 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void purgeExpired() {
        purge(LocalDateTime.now().minusYears(retentionYears));
    }

    @Transactional(rollbackFor = Exception.class)
    public int purgeClosedBefore(LocalDateTime cutoff) {
        return purge(cutoff);
    }

    private int purge(LocalDateTime cutoff) {
        int purged = 0;
        for (Bill bill : billsRepository.findAllByClosedAtBefore(cutoff)) {
            if (purgeBill(bill, cutoff)) {
                purged++;
            }
        }
        logger.info("Ledger retention purge with cutoff " + cutoff + " purged " + purged + " closed bills");
        return purged;
    }

    private boolean purgeBill(Bill bill, LocalDateTime cutoff) {
        UUID billId = bill.getId();
        Collection<Transfer> outgoing = transferRepository.getTransfersBySourceBill_Id(billId);
        Collection<Transfer> incoming = transferRepository.getTransfersByBeneficiaryBill_Id(billId);

        // A transfer is also part of the counterparty bill's history, so keep this bill
        // until every counterparty is past retention as well.
        boolean counterpartyRetained =
                outgoing.stream().anyMatch(t -> isRetained(t.getBeneficiaryBill(), cutoff)) ||
                incoming.stream().anyMatch(t -> isRetained(t.getSourceBill(), cutoff));
        if (counterpartyRetained) {
            return false;
        }

        Collection<Adjustment> adjustments = adjustmentsRepository.getAdjustmentsByBill_Id(billId);
        Collection<Payment> payments = paymentsRepository.getPaymentsByBill_Id(billId);

        LedgerPurgeRecord record = new LedgerPurgeRecord();
        record.setBillId(billId);
        record.setAccountId(bill.getAccount() == null ? null : bill.getAccount().getId());
        record.setBillClosedAt(bill.getClosedAt());
        record.setRetentionCutoff(cutoff);
        record.setPurgedAt(LocalDateTime.now());
        record.setAdjustmentsPurged(adjustments.size());
        record.setPaymentsPurged(payments.size());
        record.setTransfersPurged(outgoing.size() + incoming.size());
        purgeRecordRepository.save(record);

        adjustmentsRepository.deleteAll(adjustments);
        paymentsRepository.deleteAll(payments);
        transferRepository.deleteAll(outgoing);
        transferRepository.deleteAll(incoming);
        billsRepository.delete(bill);
        return true;
    }

    private static boolean isRetained(Bill bill, LocalDateTime cutoff) {
        return bill.getClosedAt() == null || !bill.getClosedAt().isBefore(cutoff);
    }
}
