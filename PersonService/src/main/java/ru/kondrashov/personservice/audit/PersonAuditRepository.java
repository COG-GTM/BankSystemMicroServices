package ru.kondrashov.personservice.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PersonAuditRepository extends JpaRepository<PersonAuditEvent, Long> {

    List<PersonAuditEvent> findByPersonIdOrderByOccurredAtAscIdAsc(UUID personId);
}
