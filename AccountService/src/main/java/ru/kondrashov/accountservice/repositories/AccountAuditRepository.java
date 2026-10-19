package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.AccountAuditRecord;

import java.util.List;
import java.util.UUID;

public interface AccountAuditRepository extends JpaRepository<AccountAuditRecord, UUID> {

    List<AccountAuditRecord> findByAccountIdOrderByOccurredAtAsc(UUID accountId);
}
