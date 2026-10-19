package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Optional<Account> getAccountsById(UUID id);

    Collection<Account> findAllByClosedAtIsNull();
    Collection<Account> getAccountsByPersonIdAndClosedAtIsNull(UUID personId);
    Optional<Account> getAccountsByIdAndClosedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.closedAt is null")
    Optional<Account> lockActiveAccount(@Param("id") UUID id);
}

