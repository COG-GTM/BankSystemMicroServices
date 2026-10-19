package ru.kondrashov.accountservice.entities;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit record written by {@link ru.kondrashov.accountservice.services.LedgerRetentionService}
 * for every closed bill whose transaction history is purged after the retention period.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "ledger_purge_audit", schema = "public")
public class LedgerPurgeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "account_id")
    private UUID accountId;

    @NotNull
    @Column(name = "bill_closed_at")
    private LocalDateTime billClosedAt;

    @NotNull
    @Column(name = "retention_cutoff")
    private LocalDateTime retentionCutoff;

    @NotNull
    @Column(name = "purged_at")
    private LocalDateTime purgedAt;

    @Column(name = "adjustments_purged")
    private int adjustmentsPurged;

    @Column(name = "payments_purged")
    private int paymentsPurged;

    @Column(name = "transfers_purged")
    private int transfersPurged;
}
