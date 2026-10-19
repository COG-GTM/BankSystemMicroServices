package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.AccountStatus;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

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
    public void save(Bill bill) {
        if (bill.getAccount() == null || bill.getAccount().getId() == null) {
            throw new AccountNotFoundException("Bill must reference an existing account.");
        }
        bill.setAccount(lockOpenAccount(bill.getAccount().getId()));
        billsRepository.save(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        lockOpenAccounts(accountIdOfBill(id));
        billsRepository.delete(getBill(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, Bill newBill) {
        if (newBill.getAccount() == null || newBill.getAccount().getId() == null) {
            throw new AccountNotFoundException("Bill must reference an existing account.");
        }
        lockOpenAccounts(accountIdOfBill(id), newBill.getAccount().getId());
        Bill oldBill = getBill(id);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(accountsRepository.getOne(newBill.getAccount().getId()));
        billsRepository.save(oldBill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        lockOpenAccounts(accountIdOfBill(sourceBillId), accountIdOfBill(beneficiaryBillId));
        Bill sourceBill = getBill(sourceBillId);
        Bill beneficiaryBill = getBill(beneficiaryBillId);
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
        lockOpenAccounts(accountIdOfBill(id));
        return getBill(id);
    }

    private UUID accountIdOfBill(UUID billId) {
        return billsRepository.findAccountIdByBillId(billId)
                .orElseThrow(() -> new BillNotFoundException("Bill with id = "+billId+" not found."));
    }

    // Takes the same account row lock as AccountsServiceImpl.close, before any bill is read,
    // so a close cannot interleave with a bill mutation. Sorted to avoid lock-order deadlocks.
    private void lockOpenAccounts(UUID... accountIds) {
        Stream.of(accountIds).filter(Objects::nonNull).distinct().sorted().forEach(this::lockOpenAccount);
    }

    private Account lockOpenAccount(UUID accountId) {
        Account account = accountsRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account with id = "+accountId+" not found."));
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException("Account with id = "+accountId+" is closed.");
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
