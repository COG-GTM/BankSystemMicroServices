package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Optional<Bill> getBillById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") UUID id);
}
