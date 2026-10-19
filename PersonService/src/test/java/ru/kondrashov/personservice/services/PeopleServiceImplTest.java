package ru.kondrashov.personservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.personservice.audit.AuditOperation;
import ru.kondrashov.personservice.audit.PersonAuditService;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.exception.PersonNotFoundException;
import ru.kondrashov.personservice.repositories.PeopleRepository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PeopleServiceImplTest {

    private PeopleRepository peopleRepository;
    private PersonAuditService auditService;
    private PeopleServiceImpl peopleService;

    @BeforeEach
    void setUp() {
        peopleRepository = mock(PeopleRepository.class);
        auditService = mock(PersonAuditService.class);
        peopleService = new PeopleServiceImpl(peopleRepository, auditService);
    }

    private static Person person(UUID id) {
        return new Person(id, "Mike", "Tyson", LocalDate.of(1966, 6, 30), "+7 1234859520", "miketyson@mail.com");
    }

    @Test
    void saveAuditsCreateWithGeneratedIdAndProvidedFields() {
        UUID generatedId = UUID.randomUUID();
        when(peopleRepository.save(any(Person.class))).thenReturn(person(generatedId));

        peopleService.save(person(null));

        verify(auditService).recordSuccess(AuditOperation.CREATE, generatedId,
                Arrays.asList("firstName", "lastName", "birthdate", "phoneNumber", "email"));
        verify(auditService, never()).recordFailure(any(), any(), anyCollection(), any());
    }

    @Test
    void saveFailureIsAuditedAndRethrown() {
        RuntimeException failure = new IllegalStateException("db down");
        when(peopleRepository.save(any(Person.class))).thenThrow(failure);

        assertThatThrownBy(() -> peopleService.save(person(null))).isSameAs(failure);

        verify(auditService).recordFailure(eq(AuditOperation.CREATE), eq(null), anyCollection(), eq(failure));
        verify(auditService, never()).recordSuccess(any(), any(), anyCollection());
    }

    @Test
    void updateAuditsOnlyChangedFieldNames() {
        UUID id = UUID.randomUUID();
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.of(person(id)));
        Person changed = person(null);
        changed.setEmail("new@mail.com");
        changed.setPhoneNumber("+7 0000000000");

        peopleService.update(id, changed);

        verify(auditService).recordSuccess(AuditOperation.UPDATE, id, Arrays.asList("phoneNumber", "email"));
    }

    @Test
    void updateWithNoChangesAuditsEmptyFieldList() {
        UUID id = UUID.randomUUID();
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.of(person(id)));

        peopleService.update(id, person(null));

        verify(auditService).recordSuccess(AuditOperation.UPDATE, id, Collections.emptyList());
    }

    @Test
    void updateOfUnknownPersonIsAuditedAsFailure() {
        UUID id = UUID.randomUUID();
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peopleService.update(id, person(null))).isInstanceOf(PersonNotFoundException.class);

        ArgumentCaptor<Throwable> failure = ArgumentCaptor.forClass(Throwable.class);
        verify(auditService).recordFailure(eq(AuditOperation.UPDATE), eq(id), eq(Collections.emptyList()), failure.capture());
        assertThat(failure.getValue()).isInstanceOf(PersonNotFoundException.class);
        verify(peopleRepository, never()).save(any());
    }

    @Test
    void deleteAuditsErasureOfAllPersonalDataFields() {
        UUID id = UUID.randomUUID();
        Person existing = person(id);
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.of(existing));

        peopleService.delete(id);

        verify(peopleRepository).delete(existing);
        verify(auditService).recordSuccess(AuditOperation.DELETE, id, PeopleServiceImpl.PERSONAL_DATA_FIELDS);
    }

    @Test
    void deleteOfUnknownPersonIsAuditedAsFailure() {
        UUID id = UUID.randomUUID();
        when(peopleRepository.getPersonById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> peopleService.delete(id)).isInstanceOf(PersonNotFoundException.class);

        verify(auditService).recordFailure(eq(AuditOperation.DELETE), eq(id), eq(PeopleServiceImpl.PERSONAL_DATA_FIELDS), any(PersonNotFoundException.class));
        verify(auditService, never()).recordSuccess(any(), any(), anyCollection());
    }
}
