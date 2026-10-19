package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
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

    @Override
    public Collection<Bill> getBills() {
        return billsRepository.findAllByClosedAtIsNull();
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillByIdAndClosedAtIsNull(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID accountID) {
        return billsRepository.getBillsByAccount_IdAndClosedAtIsNull(accountID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Bill bill) {
        bill.setAccount(lockActiveAccount(bill.getAccount()));
        bill.setClosedAt(null);
        billsRepository.save(bill);
    }

    /**
     * Closes the bill instead of deleting it, so its adjustments, payments and transfers
     * are retained until {@link LedgerRetentionService} purges them.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        Bill bill = lockActiveBill(id);
        bill.setClosedAt(LocalDateTime.now());
        billsRepository.save(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, Bill newBill) {
        Account account = lockActiveAccount(newBill.getAccount());
        Bill oldBill = lockActiveBill(id);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(account);
        billsRepository.save(oldBill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = lockActiveBill(billId);

       increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustment.setBill(bill);
        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = lockActiveBill(billId);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        payment.setBill(bill);
        paymentsService.save(payment);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        // Lock both bills in a fixed order so opposite-direction transfers cannot deadlock.
        boolean sourceFirst = sourceBillId.compareTo(beneficiaryBillId) <= 0;
        Bill first = lockActiveBill(sourceFirst ? sourceBillId : beneficiaryBillId);
        Bill second = lockActiveBill(sourceFirst ? beneficiaryBillId : sourceBillId);
        Bill sourceBill = sourceFirst ? first : second;
        Bill beneficiaryBill = sourceFirst ? second : first;

        decreaseBillsAmount(sourceBill, currencyConversion.exchange(transfer.getCurrency(), sourceBill.getCurrency(),transfer.getAmount()));
        increaseBillsAmount(beneficiaryBill, currencyConversion.exchange(transfer.getCurrency(), beneficiaryBill.getCurrency(),transfer.getAmount()));

        transfer.setSourceBill(sourceBill);
        transfer.setBeneficiaryBill(beneficiaryBill);
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


    /**
     * Postings, updates and closure all take this row lock, so a posting can neither
     * land on a bill closed concurrently nor write back a stale {@code closedAt}.
     */
    private Bill lockActiveBill(UUID id) {
        return billsRepository.lockActiveBill(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    /**
     * Resolves a request-supplied account to the managed, open account, so a stale
     * payload can neither attach a bill to a closed account nor reopen it via merge.
     */
    private Account lockActiveAccount(Account requested) {
        UUID accountId = requested == null ? null : requested.getId();
        if (accountId == null) {
            throw new AccountNotFoundException("Account for bill not specified.");
        }
        return accountsRepository.lockActiveAccount(accountId)
                .orElseThrow(()->new AccountNotFoundException("Account with id = "+accountId+" not found."));
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
