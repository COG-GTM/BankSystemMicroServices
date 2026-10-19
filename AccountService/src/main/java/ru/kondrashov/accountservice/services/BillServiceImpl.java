package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.BillNotDeletableException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.AdjustmentsRepository;
import ru.kondrashov.accountservice.repositories.PaymentsRepository;
import ru.kondrashov.accountservice.repositories.TransferRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillsService {

    private final BillsRepository billsRepository;
    private final AccountsRepository accountsRepository;
    private final PaymentsRepository paymentsRepository;
    private final AdjustmentsRepository adjustmentsRepository;
    private final TransferRepository transferRepository;
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
    public void save(Bill bill) {
        requireOpenAccount(bill);
        billsRepository.save(bill);
    }

    @Override
    public void delete(UUID id) {
        Bill bill = getBill(id);
        requireOpenAccount(bill);
        if (bill.getAmount() != null && bill.getAmount().signum() != 0) {
            throw new BillNotDeletableException("Bill with id = " + id + " has a non-zero balance.");
        }
        if (!paymentsRepository.getPaymentsByBill_Id(id).isEmpty()
                || !adjustmentsRepository.getAdjustmentsByBill_Id(id).isEmpty()
                || !transferRepository.getTransfersBySourceBill_Id(id).isEmpty()
                || !transferRepository.getTransfersByBeneficiaryBill_Id(id).isEmpty()) {
            throw new BillNotDeletableException("Bill with id = " + id + " has financial transactions that must be retained.");
        }
        billsRepository.delete(bill);
    }

    @Override
    public void update(UUID id, Bill newBill) {
        Bill oldBill = getBill(id);
        requireOpenAccount(oldBill);
        requireOpenAccount(newBill);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(newBill.getAccount());
        billsRepository.save(oldBill);
    }

    @Override
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = getBill(billId);

       increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = getBill(billId);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        paymentsService.save(payment);
    }


    @Override
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill = getBill(sourceBillId);
        Bill beneficiaryBill = getBill(beneficiaryBillId);

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


    private void requireOpenAccount(Bill bill){
        if (bill.getAccount() == null || bill.getAccount().getId() == null) {
            return;
        }
        UUID accountId = bill.getAccount().getId();
        boolean closed = accountsRepository.getAccountsById(accountId).map(Account::isClosed).orElse(false);
        if (closed) {
            throw new AccountClosedException("Account with id = " + accountId + " is closed.");
        }
    }

    private void decreaseBillsAmount(Bill bill, BigDecimal amount){
        requireOpenAccount(bill);

        bill.setAmount(bill.getAmount().subtract(amount));

        if(!(bill.getIsOverdraft()) && bill.getAmount().compareTo(BigDecimal.ZERO) == -1 ){
            throw new NotEnoughMoneyException("Not enough money for payment on bill with ID: " + bill.getId());
        }

        billsRepository.save(bill);
    }

    private void increaseBillsAmount(Bill bill, BigDecimal amount){
        requireOpenAccount(bill);
        bill.setAmount(bill.getAmount().add(amount));
        billsRepository.save(bill);
    }

}
