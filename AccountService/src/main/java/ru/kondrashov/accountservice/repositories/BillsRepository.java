package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.RecordStatus;

import javax.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Collection<Bill> getBillsByAccount_IdAndStatus(UUID accountId, RecordStatus status);
    Collection<Bill> getBillsByStatus(RecordStatus status);
    Optional<Bill> getBillById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.id = :id")
    Optional<Bill> lockBillById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bill b where b.account.id = :accountId and b.status = :status order by b.id")
    List<Bill> lockBillsByAccountIdAndStatus(@Param("accountId") UUID accountId, @Param("status") RecordStatus status);
}
