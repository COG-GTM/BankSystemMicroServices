package ru.kondrashov.accountservice.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.RecordStatus;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;
    private final BillsRepository billsRepository;

    public AccountsServiceImpl(AccountsRepository accountsRepository, BillsRepository billsRepository) {
        this.accountsRepository = accountsRepository;
        this.billsRepository = billsRepository;
    }


    @Override
    public Collection<Account> getAccounts() {
        return accountsRepository.getAccountsByStatus(RecordStatus.OPEN);
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonIdAndStatus(id, RecordStatus.OPEN);
    }

    @Override
    public Account getAccount(UUID id) {
        return accountsRepository.getAccountsById(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
    }

    @Override
    public void save(Account account) {
        accountsRepository.save(account);
    }

    @Override
    public void update(UUID id, Account account) {
        Account updatedAccount = getAccount(id);

        if (updatedAccount.getStatus() == RecordStatus.CLOSED) {
            throw new AccountClosedException("Account with id = "+id+" is closed.");
        }

        updatedAccount.setName(account.getName());
        updatedAccount.setCreationDate(account.getCreationDate());
        updatedAccount.setPersonId(account.getPersonId());

        accountsRepository.save(updatedAccount);
    }

    /**
     * Soft-closes the account and its bills. Rows are never hard-deleted so that payments,
     * adjustments and transfers stay available for the record-retention period.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        // Row locks serialize closure with concurrent bill creation and bill transactions.
        Account account = accountsRepository.lockAccountById(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
        if (account.getStatus() == RecordStatus.CLOSED) {
            return;
        }
        LocalDateTime closedAt = LocalDateTime.now();

        for (Bill bill : billsRepository.lockBillsByAccountIdAndStatus(id, RecordStatus.OPEN)) {
            bill.setStatus(RecordStatus.CLOSED);
            bill.setClosedAt(closedAt);
            billsRepository.save(bill);
        }

        account.setStatus(RecordStatus.CLOSED);
        account.setClosedAt(closedAt);
        accountsRepository.save(account);
    }
}
