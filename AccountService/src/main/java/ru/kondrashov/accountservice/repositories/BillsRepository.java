package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;

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
}
