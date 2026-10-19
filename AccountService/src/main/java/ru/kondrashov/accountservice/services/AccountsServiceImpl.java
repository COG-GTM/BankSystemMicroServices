package ru.kondrashov.accountservice.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.audit.AccountAuditService;
import ru.kondrashov.accountservice.audit.AuditAction;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static ru.kondrashov.accountservice.audit.AccountAuditService.snapshot;

@Service
public class AccountsServiceImpl implements AccountsService {

    private final AccountsRepository accountsRepository;
    private final AccountAuditService accountAuditService;

    public AccountsServiceImpl(AccountsRepository accountsRepository, AccountAuditService accountAuditService) {
        this.accountsRepository = accountsRepository;
        this.accountAuditService = accountAuditService;
    }


    @Override
    public Collection<Account> getAccounts() {
        return accountsRepository.findAll();
    }

    @Override
    public Collection<Account> getAccountsByPersonId(UUID id) {
        return accountsRepository.getAccountsByPersonId(id);
    }

    @Override
    public Account getAccount(UUID id) {
        return accountsRepository.getAccountsById(id).orElseThrow(()-> new AccountNotFoundException("Account with id = "+id+" not found."));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(Account account) {
        try {
            Account saved = accountsRepository.saveAndFlush(account);
            accountAuditService.recordSuccess(AuditAction.CREATE, saved.getId(), null, snapshot(saved));
        } catch (RuntimeException e) {
            recordFailure(AuditAction.CREATE, account.getId(), null, snapshot(account), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UUID id, Account account) {
        Map<String, Object> before = null;
        try {
            Account updatedAccount = getAccount(id);
            before = snapshot(updatedAccount);

            updatedAccount.setName(account.getName());
            updatedAccount.setCreationDate(account.getCreationDate());
            updatedAccount.setPersonId(account.getPersonId());

            Account saved = accountsRepository.saveAndFlush(updatedAccount);
            accountAuditService.recordSuccess(AuditAction.UPDATE, id, before, snapshot(saved));
        } catch (RuntimeException e) {
            recordFailure(AuditAction.UPDATE, id, before, snapshot(account), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID id) {
        Map<String, Object> before = null;
        try {
            Account account = getAccount(id);
            before = snapshot(account);
            accountsRepository.delete(account);
            accountsRepository.flush();
            accountAuditService.recordSuccess(AuditAction.DELETE, id, before, null);
        } catch (RuntimeException e) {
            recordFailure(AuditAction.DELETE, id, before, null, e);
            throw e;
        }
    }

    private void recordFailure(AuditAction action, UUID id, Map<String, Object> before,
                               Map<String, Object> attempted, RuntimeException cause) {
        try {
            accountAuditService.recordFailure(action, id, before, attempted, cause);
        } catch (RuntimeException auditError) {
            if (auditError != cause) {
                cause.addSuppressed(auditError);
            }
        }
    }
}
