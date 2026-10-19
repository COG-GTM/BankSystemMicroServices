package ru.kondrashov.accountservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.AccountNotSettledException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;
    private final BillsRepository billsRepository;
    private final Clock clock;

    @Autowired
    public AccountsServiceImpl(AccountsRepository accountsRepository, BillsRepository billsRepository) {
        this(accountsRepository, billsRepository, Clock.systemUTC());
    }

    AccountsServiceImpl(AccountsRepository accountsRepository, BillsRepository billsRepository, Clock clock) {
        this.accountsRepository = accountsRepository;
        this.billsRepository = billsRepository;
        this.clock = clock;
    }


    @Override
    public Collection<Account> getAccounts() {
        return accountsRepository.findAll();
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonIdAndClosedDateIsNull(id);
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
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID personId, UUID id, Account account) {
        Account updatedAccount = lockOwnedAccount(personId, id);
        if (updatedAccount.isClosed()) {
            throw new AccountClosedException("Account with id = "+id+" is closed.");
        }

        updatedAccount.setName(account.getName());
        updatedAccount.setCreationDate(account.getCreationDate());

        accountsRepository.save(updatedAccount);
    }

    /**
     * Soft-closes the account. Bills and their payments/adjustments/transfers are retained.
     * Only accounts whose bills are all at zero balance can be closed. Idempotent.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(UUID personId, UUID id) {
        Account account = lockOwnedAccount(personId, id);
        if (account.isClosed()) {
            return;
        }
        boolean unsettled = billsRepository.getBillsByAccount_Id(id).stream()
                .anyMatch(bill -> bill.getAmount() == null || bill.getAmount().signum() != 0);
        if (unsettled) {
            throw new AccountNotSettledException("Account with id = "+id+" has bills with a non-zero balance.");
        }
        account.setClosedDate(LocalDate.now(clock));
        accountsRepository.save(account);
    }

    /**
     * Locks the account row so that closure and bill postings on the same account are serialised.
     */
    private Account lockOwnedAccount(UUID personId, UUID id) {
        Account account = accountsRepository.lockById(id)
                .orElseThrow(() -> new AccountNotFoundException("Account with id = "+id+" not found."));
        if (!account.getPersonId().equals(personId)) {
            throw new AccountNotFoundException("Person with id = "+personId+" has no account with id = "+id+".");
        }
        return account;
    }
}
