package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.AccountStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Collection<Account> getAccountsByPersonIdAndStatus(UUID personId, AccountStatus status);

    Collection<Account> getAccountsByStatus(AccountStatus status);

    Optional<Account> getAccountsById(UUID id);

    Optional<Account> getAccountsByIdAndStatus(UUID id, AccountStatus status);
}
