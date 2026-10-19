package ru.kondrashov.personservice.audit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of a change to a {@link ru.kondrashov.personservice.entities.Person}.
 * Not linked by foreign key so that it outlives the erased person.
 */
@Entity
@Immutable
@Table(name = "person_audit_events", schema = "public",
        indexes = @Index(name = "idx_person_audit_events_person_id", columnList = "person_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonAuditEvent {

    /** Insertion order; tie-breaker for events with the same {@code occurredAt}. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false, updatable = false)
    private UUID personId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private PersonAuditAction action;

    @Column(nullable = false, updatable = false, length = 100)
    private String actor;

    @Column(name = "source_address", updatable = false, length = 64)
    private String sourceAddress;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "changed_fields", nullable = false, updatable = false, length = 255)
    private String changedFields;

    @Column(updatable = false, length = 2000)
    private String changes;

    public PersonAuditEvent(UUID personId, PersonAuditAction action, String actor, String sourceAddress,
                            Instant occurredAt, String changedFields, String changes) {
        this.personId = personId;
        this.action = action;
        this.actor = actor;
        this.sourceAddress = sourceAddress;
        this.occurredAt = occurredAt;
        this.changedFields = changedFields;
        this.changes = changes;
    }
}
