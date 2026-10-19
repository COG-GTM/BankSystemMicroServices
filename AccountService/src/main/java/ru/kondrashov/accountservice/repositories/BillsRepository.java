package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Optional<Bill> getBillById(UUID id);

    Collection<Bill> findAllByClosedAtIsNull();
    Collection<Bill> getBillsByAccount_IdAndClosedAtIsNull(UUID accountId);
    Optional<Bill> getBillByIdAndClosedAtIsNull(UUID id);
    Collection<Bill> findAllByClosedAtBefore(LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id and b.closedAt is null")
    Optional<Bill> lockActiveBill(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.account.id = :accountId and b.closedAt is null order by b.id")
    Collection<Bill> lockActiveBillsByAccount(@Param("accountId") UUID accountId);
}
