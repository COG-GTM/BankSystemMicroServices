package ru.kondrashov.personservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.personservice.entities.ErasureAuditEvent;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonErasedException;
import ru.kondrashov.personservice.repositories.ErasureAuditEventRepository;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PeopleServiceImplErasureTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
    private final UUID id = UUID.randomUUID();

    private PeopleRepository peopleRepository;
    private ErasureAuditEventRepository auditRepository;
    private PeopleServiceImpl service;
    private Person person;

    @BeforeEach
    void setUp() {
        peopleRepository = mock(PeopleRepository.class);
        auditRepository = mock(ErasureAuditEventRepository.class);
        service = new PeopleServiceImpl(peopleRepository, auditRepository, clock);
        person = new Person(id, "Mike", "Tyson", LocalDate.of(1966, 6, 30), "+1 555 0100", "mike@example.com");
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.of(person));
        when(peopleRepository.save(any(Person.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepository.save(any(ErasureAuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void eraseAnonymisesPiiKeepsRecordAndAudits() {
        Person erased = service.erase(id, "operator@bank");

        assertThat(erased.getId()).isEqualTo(id);
        assertThat(erased.isErased()).isTrue();
        assertThat(erased.getErasedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        assertThat(erased.getFirstName()).isEqualTo("ERASED");
        assertThat(erased.getLastName()).isEqualTo("ERASED");
        assertThat(erased.getBirthdate()).isNull();
        assertThat(erased.getPhoneNumber()).isEqualTo("ERASED");
        assertThat(erased.getEmail()).doesNotContain("mike");
        verify(peopleRepository, never()).delete(any());

        ArgumentCaptor<ErasureAuditEvent> event = ArgumentCaptor.forClass(ErasureAuditEvent.class);
        verify(auditRepository).save(event.capture());
        assertThat(event.getValue().getPersonId()).isEqualTo(id);
        assertThat(event.getValue().getActor()).isEqualTo("operator@bank");
        assertThat(event.getValue().getOutcome()).isEqualTo(ErasureAuditEvent.Outcome.COMPLETED);
        assertThat(event.getValue().getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
    }

    @Test
    void eraseIsIdempotent() {
        service.erase(id, "operator@bank");
        service.erase(id, "operator@bank");

        verify(peopleRepository, times(1)).save(any(Person.class));
        verify(auditRepository, times(1)).save(any(ErasureAuditEvent.class));
    }

    @Test
    void missingActorIsRecordedAsUnknown() {
        service.erase(id, " ");

        ArgumentCaptor<ErasureAuditEvent> event = ArgumentCaptor.forClass(ErasureAuditEvent.class);
        verify(auditRepository).save(event.capture());
        assertThat(event.getValue().getActor()).isEqualTo("unknown");
    }

    @Test
    void erasedPersonCannotBeUpdated() {
        service.erase(id, "operator@bank");

        assertThatThrownBy(() -> service.update(id, new Person(null, "New", "Name", LocalDate.of(1990, 1, 1), "1", "new@example.com")))
                .isInstanceOf(PersonErasedException.class);
    }
}
