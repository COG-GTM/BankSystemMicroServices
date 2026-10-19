package ru.kondrashov.personservice.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;

import java.util.List;
import java.util.UUID;

public interface ErasureAuditEventRepository extends JpaRepository<ErasureAuditEvent, UUID> {

    List<ErasureAuditEvent> findAllByPersonIdOrderByOccurredAtAsc(UUID personId);
}
