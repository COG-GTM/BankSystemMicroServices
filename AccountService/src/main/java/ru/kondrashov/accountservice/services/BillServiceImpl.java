package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
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
    private final PaymentsService paymentsService;
    private final AdjustmentsService adjustmentsService;
    private final TransferService transferService;
    private final FinancialCurrencyExchange currencyConversion;
    private final AccountsService accountsService;

    @Override
    public Collection<Bill> getBills() {
        return billsRepository.findAll();
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillById(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Bill getBill(UUID personId, UUID accountId, UUID billId) {
        Bill bill = getBill(billId);
        Account account = bill.getAccount();
        if (account == null || !accountId.equals(account.getId()) || !personId.equals(account.getPersonId())) {
            throw new BillNotFoundException("Bill with id = "+billId+" not found for account with id = "+accountId+".");
        }
        return bill;
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID personId, UUID accountId) {
        accountsService.getAccount(personId, accountId);
        return billsRepository.getBillsByAccount_Id(accountId);
    }

    @Override
    public void save(UUID personId, UUID accountId, Bill bill) {
        bill.setAccount(accountsService.getAccount(personId, accountId));
        billsRepository.save(bill);
    }

    @Override
    public void delete(UUID personId, UUID accountId, UUID id) {
        billsRepository.delete(getBill(personId, accountId, id));
    }

    @Override
    public void update(UUID personId, UUID accountId, UUID id, Bill newBill) {
        Bill oldBill = getBill(personId, accountId, id);
        Account newAccount = newBill.getAccount() == null || newBill.getAccount().getId() == null
                ? oldBill.getAccount()
                : accountsService.getAccount(personId, newBill.getAccount().getId());
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(newAccount);
        billsRepository.save(oldBill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAdjustment(UUID personId, UUID accountId, UUID billId, Adjustment adjustment) {

        Bill bill = getBill(personId, accountId, billId);

        increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustment.setBill(bill);
        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID personId, UUID accountId, UUID billId, Payment payment) {

        Bill bill = getBill(personId, accountId, billId);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        payment.setBill(bill);
        paymentsService.save(payment);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID personId, UUID accountId, UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill = getBill(personId, accountId, sourceBillId);
        Bill beneficiaryBill = getBill(beneficiaryBillId);

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
