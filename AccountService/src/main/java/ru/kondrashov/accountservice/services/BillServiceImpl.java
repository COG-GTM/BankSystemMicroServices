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
import ru.kondrashov.accountservice.exceptions.BillUpdateRejectedException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillsService {

    private final BillsRepository billsRepository;
    private final AccountsService accountsService;
    private final PaymentsService paymentsService;
    private final AdjustmentsService adjustmentsService;
    private final TransferService transferService;
    private final FinancialCurrencyExchange currencyConversion;

    @Override
    public Collection<Bill> getBills() {
        return billsRepository.findAll();
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillById(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID accountID) {
        return billsRepository.getBillsByAccount_Id(accountID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(UUID personId, UUID accountId, Bill bill) {
        bill.setId(null);
        bill.setAmount(BigDecimal.ZERO);
        bill.setAccount(getOwnedAccount(personId, accountId));
        billsRepository.save(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID personId, UUID accountId, UUID id) {
        billsRepository.delete(getOwnedBillForUpdate(personId, accountId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID personId, UUID accountId, UUID id, Bill newBill) {
        Bill oldBill = getOwnedBillForUpdate(personId, accountId, id);
        boolean hasBalance = oldBill.getAmount().signum() != 0;

        if (hasBalance && !oldBill.getCurrency().equals(newBill.getCurrency())) {
            throw new BillUpdateRejectedException("Currency of bill with id = " + id
                    + " cannot be changed while its balance is non-zero.");
        }
        if (!newBill.getIsOverdraft() && oldBill.getAmount().signum() < 0) {
            throw new BillUpdateRejectedException("Overdraft cannot be disabled for bill with id = " + id
                    + " while its balance is negative.");
        }

        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        billsRepository.save(oldBill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAdjustment(UUID personId, UUID accountId, UUID billId, Adjustment adjustment) {

        Bill bill = getOwnedBillForUpdate(personId, accountId, billId);

        increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustment.setBill(bill);
        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID personId, UUID accountId, UUID billId, Payment payment) {

        Bill bill = getOwnedBillForUpdate(personId, accountId, billId);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        payment.setBill(bill);
        paymentsService.save(payment);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID personId, UUID accountId, UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill;
        Bill beneficiaryBill;
        // Lock both bills in a fixed order so opposite transfers cannot deadlock.
        if (sourceBillId.compareTo(beneficiaryBillId) <= 0) {
            sourceBill = getOwnedBillForUpdate(personId, accountId, sourceBillId);
            beneficiaryBill = getBillForUpdate(beneficiaryBillId);
        } else {
            beneficiaryBill = getBillForUpdate(beneficiaryBillId);
            sourceBill = getOwnedBillForUpdate(personId, accountId, sourceBillId);
        }

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
     * Loads a bill under a row lock so balance checks and writes on it are serialized.
     * Must be called inside a transaction.
     */
    private Bill getBillForUpdate(UUID billId) {
        Bill bill = billsRepository.findBillForUpdateById(billId)
                .orElseThrow(()->new BillNotFoundException("Bill with id = "+billId+" not found."));
        if (bill.getAmount() == null) {
            bill.setAmount(BigDecimal.ZERO);
        }
        return bill;
    }

    private Bill getOwnedBillForUpdate(UUID personId, UUID accountId, UUID billId) {
        Account account = getOwnedAccount(personId, accountId);
        Bill bill = getBillForUpdate(billId);
        if (bill.getAccount() == null || !account.getId().equals(bill.getAccount().getId())) {
            throw new BillNotFoundException("Bill with id = "+billId+" not found in account with id = "+accountId+".");
        }
        return bill;
    }

    private Account getOwnedAccount(UUID personId, UUID accountId) {
        Account account = accountsService.getAccount(accountId);
        if (!account.getPersonId().equals(personId)) {
            throw new AccountNotFoundException("Account with id = "+accountId+" not found for person with id = "+personId+".");
        }
        return account;
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
