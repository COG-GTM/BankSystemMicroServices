package ru.kondrashov.accountservice.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "account_audit_log", schema = "public")
public class AccountAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountAuditAction action;

    @NotNull
    @Column(nullable = false, updatable = false)
    private String actor;

    @Column(name = "before_state", columnDefinition = "text", updatable = false)
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "text", updatable = false)
    private String afterState;

    @NotNull
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public AccountAuditRecord(UUID accountId, AccountAuditAction action, String actor, String beforeState, String afterState, LocalDateTime occurredAt) {
        this.accountId = accountId;
        this.action = action;
        this.actor = actor;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.occurredAt = occurredAt;
    }
}
