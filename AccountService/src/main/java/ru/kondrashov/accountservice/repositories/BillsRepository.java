package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.RecordStatus;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface BillsRepository extends JpaRepository<Bill, UUID> {

    Collection<Bill> getBillsByAccount_Id(UUID accountId);
    Collection<Bill> getBillsByAccount_IdAndStatus(UUID accountId, RecordStatus status);
    Collection<Bill> getBillsByStatus(RecordStatus status);
    Optional<Bill> getBillById(UUID id);
}
