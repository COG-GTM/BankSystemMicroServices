package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.repositories.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Permanently purges financial records of bills and accounts that were closed longer ago than the
 * configured retention period. Every purged row is recorded in the retention audit trail first.
 */
@Service
public class FinancialRecordRetentionService {

    private static final Logger LOGGER = LogManager.getLogger(FinancialRecordRetentionService.class);

    private final BillsRepository billsRepository;
    private final AccountsRepository accountsRepository;
    private final PaymentsRepository paymentsRepository;
    private final AdjustmentsRepository adjustmentsRepository;
    private final TransferRepository transferRepository;
    private final RetentionAuditService auditService;
    private final int retentionYears;
    private final boolean purgeEnabled;

    public FinancialRecordRetentionService(BillsRepository billsRepository,
                                           AccountsRepository accountsRepository,
                                           PaymentsRepository paymentsRepository,
                                           AdjustmentsRepository adjustmentsRepository,
                                           TransferRepository transferRepository,
                                           RetentionAuditService auditService,
                                           @Value("${retention.financial-records.years:5}") int retentionYears,
                                           @Value("${retention.financial-records.purge-enabled:true}") boolean purgeEnabled) {
        if (retentionYears < 1) {
            throw new IllegalArgumentException("retention.financial-records.years must be at least 1");
        }
        this.billsRepository = billsRepository;
        this.accountsRepository = accountsRepository;
        this.paymentsRepository = paymentsRepository;
        this.adjustmentsRepository = adjustmentsRepository;
        this.transferRepository = transferRepository;
        this.auditService = auditService;
        this.retentionYears = retentionYears;
        this.purgeEnabled = purgeEnabled;
    }

    @Scheduled(cron = "${retention.financial-records.purge-cron:0 0 3 * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void purgeExpiredRecords() {
        if (!purgeEnabled) {
            return;
        }
        purgeClosedBefore(LocalDateTime.now().minusYears(retentionYears));
    }

    @Transactional(rollbackFor = Exception.class)
    public int purgeClosedBefore(LocalDateTime cutoff) {
        Collection<Bill> expiredBills = billsRepository.findAllByClosedTrueAndClosedAtBefore(cutoff);
        Set<UUID> expiredBillIds = expiredBills.stream().map(Bill::getId).collect(Collectors.toSet());
        int purged = 0;

        for (Bill bill : expiredBills) {
            for (Payment payment : paymentsRepository.getPaymentsByBill_Id(bill.getId())) {
                audit("Payment", payment.getId(), describe(payment) + " bill=" + bill.getId());
                paymentsRepository.delete(payment);
                purged++;
            }
            for (Adjustment adjustment : adjustmentsRepository.getAdjustmentsByBill_Id(bill.getId())) {
                audit("Adjustment", adjustment.getId(), describe(adjustment) + " bill=" + bill.getId());
                adjustmentsRepository.delete(adjustment);
                purged++;
            }
        }

        Set<Transfer> transfers = new LinkedHashSet<>();
        for (UUID billId : expiredBillIds) {
            transfers.addAll(transferRepository.getTransfersBySourceBill_Id(billId));
            transfers.addAll(transferRepository.getTransfersByBeneficiaryBill_Id(billId));
        }
        for (Transfer transfer : transfers) {
            // A transfer is also part of the counterparty's history: keep it until both sides are expired.
            if (expiredBillIds.contains(transfer.getSourceBill().getId())
                    && expiredBillIds.contains(transfer.getBeneficiaryBill().getId())) {
                audit("Transfer", transfer.getId(), describe(transfer)
                        + " sourceBill=" + transfer.getSourceBill().getId()
                        + " beneficiaryBill=" + transfer.getBeneficiaryBill().getId());
                transferRepository.delete(transfer);
                purged++;
            }
        }
        transferRepository.flush();

        for (Bill bill : expiredBills) {
            if (transferRepository.getTransfersBySourceBill_Id(bill.getId()).isEmpty()
                    && transferRepository.getTransfersByBeneficiaryBill_Id(bill.getId()).isEmpty()) {
                audit("Bill", bill.getId(), "account=" + bill.getAccount().getId() + " currency=" + bill.getCurrency()
                        + " finalAmount=" + bill.getAmount() + " closedAt=" + bill.getClosedAt());
                billsRepository.delete(bill);
                purged++;
            }
        }
        billsRepository.flush();

        for (Account account : accountsRepository.findAllByClosedTrueAndClosedAtBefore(cutoff)) {
            if (billsRepository.countByAccount_Id(account.getId()) == 0) {
                audit("Account", account.getId(), "person=" + account.getPersonId() + " closedAt=" + account.getClosedAt());
                accountsRepository.delete(account);
                purged++;
            }
        }

        LOGGER.info("Retention purge for records closed before {} removed {} rows", cutoff, purged);
        return purged;
    }

    private void audit(String entityType, UUID id, String details) {
        auditService.record(RetentionAuditRecord.Action.RECORD_PURGED, entityType, id, details);
    }

    private static String describe(FinancialTransaction transaction) {
        return "amount=" + transaction.getAmount() + " currency=" + transaction.getCurrency() + " time=" + transaction.getTime();
    }
}
