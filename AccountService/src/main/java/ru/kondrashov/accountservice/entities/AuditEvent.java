package ru.kondrashov.accountservice.entities;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import ru.kondrashov.accountservice.audit.AuditOperation;
import ru.kondrashov.accountservice.audit.AuditOutcome;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a mutating bills operation. Bill ids are stored as plain columns
 * (no foreign keys) so the trail survives deletion of the bill and its transactions.
 */
@Entity
@Immutable
@Table(name = "audit_events", schema = "public")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private AuditOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AuditOutcome outcome;

    @Column(updatable = false, length = 512)
    private String failureReason;

    @Column(nullable = false, updatable = false, length = 128)
    private String actor;

    @Column(updatable = false, length = 128)
    private String approvedBy;

    @Column(updatable = false)
    private UUID personId;

    @Column(updatable = false)
    private UUID accountId;

    @Column(updatable = false)
    private UUID billId;

    @Column(updatable = false)
    private UUID counterpartyBillId;

    @Column(updatable = false)
    private UUID transactionId;

    @Column(updatable = false)
    private BigDecimal amount;

    @Column(updatable = false, length = 3)
    private String currency;

    @Column(updatable = false)
    private BigDecimal balanceBefore;

    @Column(updatable = false)
    private BigDecimal balanceAfter;

    @Column(updatable = false)
    private BigDecimal counterpartyBalanceBefore;

    @Column(updatable = false)
    private BigDecimal counterpartyBalanceAfter;

    @Column(updatable = false, length = 128)
    private String requestId;

    @Column(updatable = false, length = 64)
    private String clientAddress;
}
