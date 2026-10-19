package ru.kondrashov.accountservice.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit trail for closures and retention purges of financial records.
 * Deliberately holds no foreign keys so entries outlive the records they describe.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "retention_audit", schema = "public")
public class RetentionAuditRecord {

    public enum Action { ACCOUNT_CLOSED, BILL_CLOSED, RECORD_PURGED }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @NotNull
    @Column(nullable = false)
    private String entityType;

    @NotNull
    @Column(nullable = false)
    private UUID entityId;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime time;

    @Column(length = 1024)
    private String details;

    public RetentionAuditRecord(Action action, String entityType, UUID entityId, LocalDateTime time, String details) {
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.time = time;
        this.details = details;
    }
}
