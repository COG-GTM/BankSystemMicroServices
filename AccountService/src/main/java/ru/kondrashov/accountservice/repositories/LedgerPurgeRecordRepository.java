package ru.kondrashov.accountservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.accountservice.entities.LedgerPurgeRecord;

import java.util.Collection;
import java.util.UUID;

public interface LedgerPurgeRecordRepository extends JpaRepository<LedgerPurgeRecord, UUID> {

    Collection<LedgerPurgeRecord> getLedgerPurgeRecordsByBillId(UUID billId);
}
