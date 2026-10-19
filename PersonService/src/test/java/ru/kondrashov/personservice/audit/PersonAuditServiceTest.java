package ru.kondrashov.personservice.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ru.kondrashov.personservice.exception.PersonNotFoundException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonAuditServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private PersonAuditService auditService;

    @BeforeEach
    void setUp() {
        PersonAuditRepository repository = mock(PersonAuditRepository.class);
        when(repository.save(any(PersonAuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        auditService = new PersonAuditService(repository, new AuditActorResolver(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindRequest(String actor, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (actor != null) {
            request.addHeader(AuditActorResolver.ACTOR_HEADER, actor);
        }
        request.setRemoteAddr(remoteAddr);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void successEventCarriesActorTargetFieldsOutcomeAndTimestamp() {
        bindRequest("teller-42", "10.0.0.7");
        UUID personId = UUID.randomUUID();

        PersonAuditEvent event = auditService.recordSuccess(AuditOperation.UPDATE, personId, Arrays.asList("phoneNumber", "email"));

        assertThat(event.getActor()).isEqualTo("teller-42");
        assertThat(event.getClientAddress()).isEqualTo("10.0.0.7");
        assertThat(event.getOperation()).isEqualTo(AuditOperation.UPDATE);
        assertThat(event.getPersonId()).isEqualTo(personId);
        assertThat(event.getChangedFields()).isEqualTo("phoneNumber,email");
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getFailureReason()).isNull();
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    void failureEventRecordsExceptionTypeOnly() {
        bindRequest(null, "10.0.0.8");
        UUID personId = UUID.randomUUID();

        PersonAuditEvent event = auditService.recordFailure(AuditOperation.DELETE, personId, Collections.emptyList(),
                new PersonNotFoundException("Person with id = " + personId + " not found"));

        assertThat(event.getActor()).isEqualTo(AuditActorResolver.ANONYMOUS);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.getFailureReason()).isEqualTo("PersonNotFoundException");
        assertThat(event.getChangedFields()).isNull();
    }

    @Test
    void outsideRequestActorIsSystem() {
        PersonAuditEvent event = auditService.recordSuccess(AuditOperation.CREATE, UUID.randomUUID(), Collections.singletonList("email"));

        assertThat(event.getActor()).isEqualTo(AuditActorResolver.SYSTEM);
        assertThat(event.getClientAddress()).isNull();
    }

    @Test
    void actorHeaderIsSanitizedAndTruncated() {
        StringBuilder longActor = new StringBuilder("evil\nactor=admin outcome=SUCCESS");
        for (int i = 0; i < 200; i++) {
            longActor.append('x');
        }
        bindRequest(longActor.toString(), "10.0.0.9");

        PersonAuditEvent event = auditService.recordSuccess(AuditOperation.CREATE, UUID.randomUUID(), Collections.emptyList());

        assertThat(event.getActor()).doesNotContain("\n").doesNotContain(" ").startsWith("evil_actor=admin_outcome=SUCCESS");
        assertThat(event.getActor()).hasSize(AuditActorResolver.MAX_ACTOR_LENGTH);
    }

    @Test
    void blankActorHeaderFallsBackToAnonymous() {
        bindRequest("   ", "10.0.0.10");

        assertThat(new AuditActorResolver().currentActor()).isEqualTo(AuditActorResolver.ANONYMOUS);
    }
}
