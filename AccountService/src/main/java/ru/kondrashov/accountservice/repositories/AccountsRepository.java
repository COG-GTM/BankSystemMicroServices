package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.RecordStatus;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Collection<Account> getAccountsByPersonIdAndStatus(UUID personId, RecordStatus status);

    Collection<Account> getAccountsByStatus(RecordStatus status);

    Optional<Account> getAccountsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> lockAccountById(@Param("id") UUID id);
}

