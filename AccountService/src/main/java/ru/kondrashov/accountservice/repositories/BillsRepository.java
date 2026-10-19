package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Optional<Bill> getBillById(UUID id);

    @Query("select b.account.id from Bill b where b.id = :id")
    Optional<UUID> findAccountIdByBillId(@Param("id") UUID id);
}
