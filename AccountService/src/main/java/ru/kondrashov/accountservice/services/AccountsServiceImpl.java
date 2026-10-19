package ru.kondrashov.accountservice.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.RetentionAuditRecord;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;
    private final BillsService billsService;
    private final RetentionAuditService auditService;

    public AccountsServiceImpl(AccountsRepository accountsRepository, BillsService billsService, RetentionAuditService auditService) {
        this.accountsRepository = accountsRepository;
        this.billsService = billsService;
        this.auditService = auditService;
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
        return accountsRepository.getAccountsByIdAndClosedFalse(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(UUID id) {
        Account account = accountsRepository.findLockedByIdAndClosedFalse(id)
                .orElseThrow(() -> new AccountNotFoundException("Account with id = " + id + " not found."));
        billsService.closeAllByAccountId(id);
        account.setClosed(true);
        account.setClosedAt(LocalDateTime.now());
        accountsRepository.save(account);
        auditService.record(RetentionAuditRecord.Action.ACCOUNT_CLOSED, "Account", account.getId(), "person=" + account.getPersonId());
    }
}
