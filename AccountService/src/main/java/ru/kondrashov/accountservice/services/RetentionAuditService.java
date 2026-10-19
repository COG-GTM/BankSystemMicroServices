package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import ru.kondrashov.accountservice.entities.RetentionAuditRecord;
import ru.kondrashov.accountservice.repositories.RetentionAuditRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RetentionAuditService {

    private static final Logger AUDIT_LOGGER = LogManager.getLogger("ru.kondrashov.accountservice.audit.retention");

    private final RetentionAuditRepository retentionAuditRepository;

    public RetentionAuditService(RetentionAuditRepository retentionAuditRepository) {
        this.retentionAuditRepository = retentionAuditRepository;
    }

    public void record(RetentionAuditRecord.Action action, String entityType, UUID entityId, String details) {
        retentionAuditRepository.save(new RetentionAuditRecord(action, entityType, entityId, LocalDateTime.now(), details));
        AUDIT_LOGGER.info("{} {} id={} {}", action, entityType, entityId, details);
    }
}
