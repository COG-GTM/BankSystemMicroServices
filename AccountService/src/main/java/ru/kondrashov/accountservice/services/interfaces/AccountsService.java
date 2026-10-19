package ru.kondrashov.accountservice.services.interfaces;

import ru.kondrashov.accountservice.entities.Account;

import java.util.Collection;
import java.util.UUID;

public interface AccountsService {

    Collection<Account> getAccounts();
    Collection<Account> getAccountsByPersonId(UUID id);
    Account getAccount(UUID id);
    Account getAccount(UUID personId, UUID id);
    void save(Account account);
    void save(UUID personId, Account account);
    void update(UUID id, Account account);
    void update(UUID personId, UUID id, Account account);
    void delete(UUID id);
    void delete(UUID personId, UUID id);
}
