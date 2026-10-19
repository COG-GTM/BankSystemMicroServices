package ru.kondrashov.accountservice.audit;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record of an account mutation. Rows are never updated or deleted by the application.
 */
@Entity
@Immutable
@Table(name = "account_audit_events", schema = "public", indexes = {
        @Index(name = "idx_account_audit_events_account_id", columnList = "account_id"),
        @Index(name = "idx_account_audit_events_occurred_at", columnList = "occurred_at")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AccountAuditEvent {

    static final int STATE_LENGTH = 2000;
    static final int REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditOutcome outcome;

    @Column(nullable = false, updatable = false, length = AuditActorResolver.MAX_VALUE_LENGTH)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_source", nullable = false, updatable = false, length = 16)
    private ActorSource actorSource;

    @Column(name = "client_address", updatable = false, length = AuditActorResolver.MAX_VALUE_LENGTH)
    private String clientAddress;

    @Column(name = "request_id", updatable = false, length = AuditActorResolver.MAX_VALUE_LENGTH)
    private String requestId;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    @Column(name = "person_id", updatable = false)
    private UUID personId;

    @Column(name = "before_state", updatable = false, length = STATE_LENGTH)
    private String beforeState;

    @Column(name = "after_state", updatable = false, length = STATE_LENGTH)
    private String afterState;

    @Column(name = "failure_reason", updatable = false, length = REASON_LENGTH)
    private String failureReason;
}
