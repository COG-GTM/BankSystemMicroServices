package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.RetentionAuditRecord;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillsService {

    private final BillsRepository billsRepository;
    private final AccountsRepository accountsRepository;
    private final PaymentsService paymentsService;
    private final AdjustmentsService adjustmentsService;
    private final TransferService transferService;
    private final FinancialCurrencyExchange currencyConversion;
    private final RetentionAuditService auditService;

    @Override
    public Collection<Bill> getBills() {
        return billsRepository.findAllByClosedFalse();
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillByIdAndClosedFalse(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID accountID) {
        return billsRepository.getBillsByAccount_IdAndClosedFalse(accountID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Bill bill) {
        bill.setAccount(getOpenAccount(bill.getAccount()));
        billsRepository.save(bill);
    }

    private Bill getBillForUpdate(UUID id) {
        return billsRepository.findLockedByIdAndClosedFalse(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    private Account getOpenAccount(Account account) {
        UUID accountId = account == null ? null : account.getId();
        return (accountId == null ? java.util.Optional.<Account>empty() : accountsRepository.findLockedByIdAndClosedFalse(accountId))
                .orElseThrow(() -> new AccountNotFoundException("Account with id = " + accountId + " not found."));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(UUID id) {
        close(getBillForUpdate(id));
    }

    // Caller must hold the account lock (AccountsServiceImpl.close); bills are locked in UUID order like commitTransfer.
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeAllByAccountId(UUID accountId) {
        billsRepository.getBillsByAccount_IdAndClosedFalse(accountId).stream()
                .map(Bill::getId)
                .sorted()
                .forEach(billId -> billsRepository.findLockedByIdAndClosedFalse(billId)
                        .filter(bill -> bill.getAccount().getId().equals(accountId))
                        .ifPresent(this::close));
    }

    private void close(Bill bill) {
        bill.setClosed(true);
        bill.setClosedAt(LocalDateTime.now());
        billsRepository.save(bill);
        auditService.record(RetentionAuditRecord.Action.BILL_CLOSED, "Bill", bill.getId(),
                "account=" + bill.getAccount().getId() + " currency=" + bill.getCurrency() + " finalAmount=" + bill.getAmount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, Bill newBill) {
        Account account = getOpenAccount(newBill.getAccount());
        Bill oldBill = getBillForUpdate(id);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(account);
        billsRepository.save(oldBill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = getBillForUpdate(billId);

       increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = getBillForUpdate(billId);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        paymentsService.save(payment);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        // Lock both bills in a fixed order so concurrent opposite-direction transfers cannot deadlock.
        boolean sourceFirst = sourceBillId.compareTo(beneficiaryBillId) <= 0;
        Bill first = getBillForUpdate(sourceFirst ? sourceBillId : beneficiaryBillId);
        Bill second = getBillForUpdate(sourceFirst ? beneficiaryBillId : sourceBillId);
        Bill sourceBill = sourceFirst ? first : second;
        Bill beneficiaryBill = sourceFirst ? second : first;

        decreaseBillsAmount(sourceBill, currencyConversion.exchange(transfer.getCurrency(), sourceBill.getCurrency(),transfer.getAmount()));
        increaseBillsAmount(beneficiaryBill, currencyConversion.exchange(transfer.getCurrency(), beneficiaryBill.getCurrency(),transfer.getAmount()));

        transferService.save(transfer);
    }

    @Override
    public Collection<Payment> getPaymentsByBillId(UUID billId) {
        return paymentsService.getPaymentsByBillId(billId);
    }

    @Override
    public Collection<Adjustment> getAdjustmentsByBillId(UUID billId) {
        return adjustmentsService.getAdjustmentsByBillId(billId);
    }

    @Override
    public Collection<Transfer> getTransfersBySourceBillId(UUID billId) {
        return transferService.getTransfersBySourceBill_Id(billId);
    }

    @Override
    public Collection<Transfer> getTransfersByBeneficiaryBillId(UUID billId) {
        return transferService.getTransfersByBeneficiaryBill_Id(billId);
    }


    private void decreaseBillsAmount(Bill bill, BigDecimal amount){

        bill.setAmount(bill.getAmount().subtract(amount));

        if(!(bill.getIsOverdraft()) && bill.getAmount().compareTo(BigDecimal.ZERO) == -1 ){
            throw new NotEnoughMoneyException("Not enough money for payment on bill with ID: " + bill.getId());
        }

        billsRepository.save(bill);
    }

    private void increaseBillsAmount(Bill bill, BigDecimal amount){
        bill.setAmount(bill.getAmount().add(amount));
        billsRepository.save(bill);
    }

}
