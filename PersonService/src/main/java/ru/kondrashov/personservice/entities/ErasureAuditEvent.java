package ru.kondrashov.personservice.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only record of a customer erasure attempt: who requested it, for which person, when and with what outcome.
 */
@Entity
@Table(name = "erasure_audit_events", schema = "public")
@Data
@NoArgsConstructor
public class ErasureAuditEvent {

    public enum Outcome { STARTED, COMPLETED, REJECTED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID personId;

    @Column(nullable = false, updatable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Outcome outcome;

    @Column(length = 2000, updatable = false)
    private String detail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public ErasureAuditEvent(UUID personId, String actor, Outcome outcome, String detail, LocalDateTime occurredAt) {
        this.personId = personId;
        this.actor = actor;
        this.outcome = outcome;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }
}
