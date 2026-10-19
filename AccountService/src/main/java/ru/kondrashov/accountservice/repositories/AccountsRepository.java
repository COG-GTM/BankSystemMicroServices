package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.Account;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Optional<Account> getAccountsById(UUID id);

    Collection<Account> findAllByClosedFalse();

    Collection<Account> getAccountsByPersonIdAndClosedFalse(UUID personId);

    Optional<Account> getAccountsByIdAndClosedFalse(UUID id);

    Collection<Account> findAllByClosedTrueAndClosedAtBefore(LocalDateTime cutoff);
}
