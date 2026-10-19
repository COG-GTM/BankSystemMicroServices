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
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotDeletableException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.AdjustmentsRepository;
import ru.kondrashov.accountservice.repositories.PaymentsRepository;
import ru.kondrashov.accountservice.repositories.TransferRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Arrays;
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
    private final EntityManager entityManager;
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
        bill.setAccount(lockOpenAccount(personId, accountId));
        if (bill.getAmount() == null) {
            bill.setAmount(BigDecimal.ZERO);
        }
        billsRepository.save(bill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID personId, UUID accountId, UUID id) {
        lockOpenAccount(personId, accountId);
        Bill bill = getBillOfAccount(accountId, id);
        if (bill.getAmount() == null || bill.getAmount().signum() != 0) {
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
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID personId, UUID accountId, UUID id, Bill newBill) {
        lockOpenAccount(personId, accountId);
        Bill oldBill = getBillOfAccount(accountId, id);
        // The balance only changes through recorded postings; a currency change converts the stored balance.
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),oldBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        billsRepository.save(oldBill);
    }

    @Override
    public void requireBillOfAccount(UUID personId, UUID accountId, UUID billId) {
        Account account = accountsRepository.getAccountsById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account with id = " + accountId + " not found."));
        if (!account.getPersonId().equals(personId)) {
            throw new AccountNotFoundException("Person with id = " + personId + " has no account with id = " + accountId + ".");
        }
        getBillOfAccount(accountId, billId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = getBill(billId);
        lockOpenAccounts(bill);

        increaseBillsAmount(bill,currencyConversion.exchange(adjustment.getCurrency(),bill.getCurrency(),adjustment.getAmount()));

        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = getBill(billId);
        lockOpenAccounts(bill);

        decreaseBillsAmount(bill,currencyConversion.exchange(payment.getCurrency(),bill.getCurrency(),payment.getAmount()));

        paymentsService.save(payment);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill = getBill(sourceBillId);
        Bill beneficiaryBill = getBill(beneficiaryBillId);
        lockOpenAccounts(sourceBill, beneficiaryBill);

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


    private Bill getBillOfAccount(UUID accountId, UUID billId) {
        Bill bill = getBill(billId);
        if (bill.getAccount() == null || !accountId.equals(bill.getAccount().getId())) {
            throw new BillNotFoundException("Account with id = " + accountId + " has no bill with id = " + billId + ".");
        }
        return bill;
    }

    private Account lockOpenAccount(UUID personId, UUID accountId) {
        Account account = accountsRepository.lockById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account with id = " + accountId + " not found."));
        if (!account.getPersonId().equals(personId)) {
            throw new AccountNotFoundException("Person with id = " + personId + " has no account with id = " + accountId + ".");
        }
        if (account.isClosed()) {
            throw new AccountClosedException("Account with id = " + accountId + " is closed.");
        }
        return account;
    }

    /**
     * Locks the accounts owning the given bills (in a stable order to avoid deadlocks) and verifies they are all
     * open before any balance is changed. Serialises postings with account closure.
     */
    private void lockOpenAccounts(Bill... bills) {
        Arrays.stream(bills)
                .map(bill -> bill.getAccount().getId())
                .distinct()
                .sorted()
                .forEach(accountId -> {
                    Account account = accountsRepository.lockById(accountId)
                            .orElseThrow(() -> new AccountNotFoundException("Account with id = " + accountId + " not found."));
                    if (account.isClosed()) {
                        throw new AccountClosedException("Account with id = " + accountId + " is closed.");
                    }
                });
        Arrays.stream(bills).forEach(entityManager::refresh);
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
