package ru.kondrashov.personservice.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.kondrashov.personservice.entities.Person;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PersonAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:15:30Z");

    private final PersonAuditRepository repository = mock(PersonAuditRepository.class);
    private final PersonAuditService service =
            new PersonAuditService(repository, new AuditActorProvider(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID id = UUID.randomUUID();
    private final Person person = new Person(id, "Mike", "Tyson", LocalDate.of(1966, 6, 30),
            "+7 1234859520", "miketyson@mail.com");

    @BeforeEach
    void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuditActorProvider.ACTOR_HEADER, "teller-42");
        request.setRemoteAddr("10.0.0.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PersonAuditEvent captured() {
        ArgumentCaptor<PersonAuditEvent> captor = ArgumentCaptor.forClass(PersonAuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createRecordsActorTimestampAndFieldNamesOnly() {
        service.recordCreate(person);

        PersonAuditEvent event = captured();
        assertThat(event.getPersonId()).isEqualTo(id);
        assertThat(event.getAction()).isEqualTo(PersonAuditAction.CREATE);
        assertThat(event.getActor()).isEqualTo("teller-42");
        assertThat(event.getSourceAddress()).isEqualTo("10.0.0.7");
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getChangedFields()).isEqualTo("firstName,lastName,birthdate,phoneNumber,email");
        assertThat(event.getChanges()).isNull();
    }

    @Test
    void updateRecordsOnlyChangedFieldsWithMaskedValues() {
        Person after = new Person(id, "John", "Tyson", LocalDate.of(1966, 6, 30),
                "+7 1234859520", "johnjones@mail.com");

        service.recordUpdate(person, after);

        PersonAuditEvent event = captured();
        assertThat(event.getAction()).isEqualTo(PersonAuditAction.UPDATE);
        assertThat(event.getChangedFields()).isEqualTo("firstName,email");
        assertThat(event.getChanges()).isEqualTo("firstName: M***e -> J***n; email: m***m -> j***m");
        assertThat(event.getChanges()).doesNotContain("Mike", "John", "miketyson", "johnjones");
    }

    @Test
    void updateWithoutChangesWritesNoEvent() {
        Person same = new Person(id, "Mike", "Tyson", LocalDate.of(1966, 6, 30),
                "+7 1234859520", "miketyson@mail.com");

        service.recordUpdate(person, same);

        verify(repository, never()).save(any());
    }

    @Test
    void deleteRecordsErasureWithoutRetainingPersonalData() {
        service.recordDelete(person);

        PersonAuditEvent event = captured();
        assertThat(event.getAction()).isEqualTo(PersonAuditAction.DELETE);
        assertThat(event.getPersonId()).isEqualTo(id);
        assertThat(event.getActor()).isEqualTo("teller-42");
        assertThat(event.getChangedFields()).isEqualTo("firstName,lastName,birthdate,phoneNumber,email");
        assertThat(event.getChanges()).isNull();
    }

    @Test
    void actorFallsBackToAnonymousOrSystem() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        service.recordDelete(person);
        RequestContextHolder.resetRequestAttributes();
        service.recordDelete(person);

        ArgumentCaptor<PersonAuditEvent> captor = ArgumentCaptor.forClass(PersonAuditEvent.class);
        verify(repository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getActor()).isEqualTo("anonymous");
        assertThat(captor.getAllValues().get(1).getActor()).isEqualTo("system");
        assertThat(captor.getAllValues().get(1).getSourceAddress()).isNull();
    }

    @Test
    void actorHeaderIsStrippedOfControlCharactersAndTruncated() {
        assertThat(AuditActorProvider.sanitize("evil\r\nINFO forged")).isEqualTo("evilINFO forged");
        assertThat(AuditActorProvider.sanitize(new String(new char[150]).replace('\0', 'a'))).hasSize(100);
    }

    @Test
    void maskHidesShortValues() {
        assertThat(PersonAuditService.mask(null)).isEqualTo("null");
        assertThat(PersonAuditService.mask("Al")).isEqualTo("***");
        assertThat(PersonAuditService.mask(LocalDate.of(1987, 7, 19))).isEqualTo("1***9");
    }
}
