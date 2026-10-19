package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.kondrashov.accountservice.entities.Account;

import javax.persistence.LockModeType;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findLockedByIdAndClosedFalse(UUID id);
}
