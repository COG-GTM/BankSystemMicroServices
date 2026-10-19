package ru.kondrashov.accountservice.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
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
        return accountsRepository.findAllByClosedAtIsNull();
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonIdAndClosedAtIsNull(id);
    }

    @Override
    public Account getAccount(UUID id) {
        return accountsRepository.getAccountsByIdAndClosedAtIsNull(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
    }

    @Override
    public void save(Account account) {
        accountsRepository.save(account);
    }

    @Override
    public void update(UUID id, Account account) {
        Account updatedAccount = getAccount(id);

        updatedAccount.setName(account.getName());
        updatedAccount.setCreationDate(account.getCreationDate());
        updatedAccount.setPersonId(account.getPersonId());

        accountsRepository.save(updatedAccount);
    }

    /**
     * Closes the account and its open bills instead of deleting them, so their
     * transaction history is retained until {@link LedgerRetentionService} purges it.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        Account account = accountsRepository.lockActiveAccount(id)
                .orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
        LocalDateTime closedAt = LocalDateTime.now();
        for (Bill bill : billsRepository.lockActiveBillsByAccount(id)) {
            bill.setClosedAt(closedAt);
            billsRepository.save(bill);
        }
        account.setClosedAt(closedAt);
        accountsRepository.save(account);
    }
}
