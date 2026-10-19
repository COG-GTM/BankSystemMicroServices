package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.*;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillClosedException;
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
        return billsRepository.getBillsByStatus(RecordStatus.OPEN);
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillById(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID accountID) {
        return billsRepository.getBillsByAccount_IdAndStatus(accountID, RecordStatus.OPEN);
    }

    @Override
    public void save(Bill bill) {
        bill.setAccount(getOpenAccount(bill.getAccount()));
        bill.setStatus(RecordStatus.OPEN);
        bill.setClosedAt(null);
        billsRepository.save(bill);
    }

    /**
     * Soft-closes the bill. Bills are never hard-deleted so that their payments, adjustments
     * and transfers (including the counterparty side) stay available for the retention period.
     */
    @Override
    public void delete(UUID id) {
        Bill bill = getBill(id);
        if (bill.getStatus() == RecordStatus.CLOSED) {
            return;
        }
        bill.setStatus(RecordStatus.CLOSED);
        bill.setClosedAt(LocalDateTime.now());
        billsRepository.save(bill);
    }

    @Override
    public void update(UUID id, Bill newBill) {
        Bill oldBill = getOpenBill(id);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(getOpenAccount(newBill.getAccount()));
        billsRepository.save(oldBill);
    }

    @Override
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = getOpenBill(billId);
        adjustment.setBill(bill);

       increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = getOpenBill(billId);
        payment.setBill(bill);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        paymentsService.save(payment);
    }


    @Override
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill = getOpenBill(sourceBillId);
        Bill beneficiaryBill = getOpenBill(beneficiaryBillId);
        transfer.setSourceBill(sourceBill);
        transfer.setBeneficiaryBill(beneficiaryBill);

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

    private Bill getOpenBill(UUID id) {
        Bill bill = getBill(id);
        if (bill.getStatus() == RecordStatus.CLOSED) {
            throw new BillClosedException("Bill with id = "+id+" is closed.");
        }
        return bill;
    }

    private Account getOpenAccount(Account requested) {
        if (requested == null || requested.getId() == null) {
            throw new AccountNotFoundException("Bill must reference an existing account.");
        }
        UUID id = requested.getId();
        Account account = accountsRepository.getAccountsById(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
        if (account.getStatus() == RecordStatus.CLOSED) {
            throw new AccountClosedException("Account with id = "+id+" is closed.");
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
