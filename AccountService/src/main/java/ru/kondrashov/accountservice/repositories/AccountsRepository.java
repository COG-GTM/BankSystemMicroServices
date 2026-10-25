package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AccountsRepository extends JpaRepository<Account, UUID> {

    Collection<Account> getAccountsByPersonId(UUID personId);

    Optional<Account> getAccountsById(UUID id);

    @Modifying
    @Query("delete from Account a where a.personId = :personId")
    int deleteAllByPersonIdInBulk(@Param("personId") UUID personId);
}

