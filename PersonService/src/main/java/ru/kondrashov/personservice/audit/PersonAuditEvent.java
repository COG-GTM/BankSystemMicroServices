package ru.kondrashov.personservice.audit;

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
 * Append-only record of a create/update/delete of a {@link ru.kondrashov.personservice.entities.Person}.
 * Holds the names of the changed fields only, never their values.
 */
@Entity
@Immutable
@Table(name = "person_audit_events", schema = "public",
        indexes = @Index(name = "idx_person_audit_events_person_id", columnList = "personId"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PersonAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(updatable = false, nullable = false)
    private Instant occurredAt;

    @Column(updatable = false, nullable = false, length = 128)
    private String actor;

    @Column(updatable = false, length = 64)
    private String clientAddress;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, nullable = false, length = 16)
    private AuditOperation operation;

    @Column(updatable = false)
    private UUID personId;

    @Column(updatable = false, length = 255)
    private String changedFields;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, nullable = false, length = 16)
    private AuditOutcome outcome;

    @Column(updatable = false, length = 128)
    private String failureReason;
}
