package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.kondrashov.accountservice.entities.Bill;

import javax.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Optional<Bill> getBillById(UUID id);

    Collection<Bill> findAllByClosedFalse();
    Collection<Bill> getBillsByAccount_IdAndClosedFalse(UUID accountId);
    Optional<Bill> getBillByIdAndClosedFalse(UUID id);
    Collection<Bill> findAllByClosedTrueAndClosedAtBefore(LocalDateTime cutoff);
    long countByAccount_Id(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Bill> findLockedByIdAndClosedFalse(UUID id);
}
