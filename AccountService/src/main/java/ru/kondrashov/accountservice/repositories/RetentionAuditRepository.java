package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.RetentionAuditRecord;

import java.util.Collection;
import java.util.UUID;

public interface RetentionAuditRepository extends JpaRepository<RetentionAuditRecord, UUID> {

    Collection<RetentionAuditRecord> findAllByEntityId(UUID entityId);
}
