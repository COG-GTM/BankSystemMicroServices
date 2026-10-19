package ru.kondrashov.accountservice.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
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
        return accountsRepository.findAllByClosedFalse();
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonIdAndClosedFalse(id);
    }

    @Override
    public Account getAccount(UUID id) {
        return accountsRepository.getAccountsById(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
    }

    @Override
    public void save(Account account) {
        account.setClosed(false);
        account.setClosedAt(null);
        accountsRepository.save(account);
    }

    @Override
    public void update(UUID id, Account account) {
        Account updatedAccount = getAccount(id);

        if (updatedAccount.isClosed()) {
            throw new AccountClosedException("Account with id = "+id+" is closed.");
        }

        updatedAccount.setName(account.getName());
        updatedAccount.setCreationDate(account.getCreationDate());
        updatedAccount.setPersonId(account.getPersonId());

        accountsRepository.save(updatedAccount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(UUID id) {
        Account account = accountsRepository.findByIdForUpdate(id)
                .orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
        if (account.isClosed()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        for (Bill bill : billsRepository.findOpenByAccountIdForUpdate(id)) {
            bill.setClosed(true);
            bill.setClosedAt(now);
            billsRepository.save(bill);
        }

        account.setClosed(true);
        account.setClosedAt(now);
        accountsRepository.save(account);
    }
}
