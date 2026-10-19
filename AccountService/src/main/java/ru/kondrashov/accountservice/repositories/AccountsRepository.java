package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.RecordStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Collection<Account> getAccountsByPersonIdAndStatus(UUID personId, RecordStatus status);

    Collection<Account> getAccountsByStatus(RecordStatus status);

    Optional<Account> getAccountsById(UUID id);
}

