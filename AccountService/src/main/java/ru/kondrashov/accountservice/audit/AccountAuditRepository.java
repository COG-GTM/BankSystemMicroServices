package ru.kondrashov.accountservice.audit;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Insert/read-only repository: deliberately exposes no update or delete operations.
 */
public interface AccountAuditRepository extends Repository<AccountAuditEvent, UUID> {

    AccountAuditEvent save(AccountAuditEvent event);

    List<AccountAuditEvent> findByAccountIdOrderByOccurredAtAsc(UUID accountId);

    List<AccountAuditEvent> findAll();
}
