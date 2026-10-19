package ru.kondrashov.accountservice.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.services.AccountsServiceImpl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountsServiceAuditTest {

    private static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private AccountsRepository accountsRepository;
    private AccountAuditRepository auditRepository;
    private AuditActorResolver actorResolver;
    private AccountsServiceImpl service;

    @BeforeEach
    void setUp() {
        accountsRepository = mock(AccountsRepository.class);
        auditRepository = mock(AccountAuditRepository.class);
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        actorResolver = mock(AuditActorResolver.class);
        when(actorResolver.resolve()).thenReturn(new AuditActor("teller-7", ActorSource.HEADER, "10.0.0.1", "req-42"));
        AccountAuditService auditService = new AccountAuditService(auditRepository, actorResolver,
                Clock.fixed(NOW, ZoneOffset.UTC), null);
        service = new AccountsServiceImpl(accountsRepository, auditService);
    }

    private static Account account(UUID id, String name, UUID personId) {
        Account account = new Account(name, LocalDate.of(2021, 4, 23), personId);
        account.setId(id);
        return account;
    }

    private AccountAuditEvent onlyEvent() {
        ArgumentCaptor<AccountAuditEvent> captor = ArgumentCaptor.forClass(AccountAuditEvent.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void create_recordsActorAccountAndAfterState() throws Exception {
        UUID id = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        when(accountsRepository.saveAndFlush(any())).thenReturn(account(id, "Salary", personId));

        service.save(account(null, "Salary", personId));

        AccountAuditEvent event = onlyEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getActor()).isEqualTo("teller-7");
        assertThat(event.getActorSource()).isEqualTo(ActorSource.HEADER);
        assertThat(event.getClientAddress()).isEqualTo("10.0.0.1");
        assertThat(event.getRequestId()).isEqualTo("req-42");
        assertThat(event.getOccurredAt()).isEqualTo(NOW);
        assertThat(event.getAccountId()).isEqualTo(id);
        assertThat(event.getPersonId()).isEqualTo(personId);
        assertThat(event.getBeforeState()).isNull();
        JsonNode after = json.readTree(event.getAfterState());
        assertThat(after.get("id").asText()).isEqualTo(id.toString());
        assertThat(after.get("name").asText()).isEqualTo("Salary");
        assertThat(after.get("creationDate").asText()).isEqualTo("2021-04-23");
    }

    @Test
    void update_recordsBeforeAndAfterIncludingReassignment() throws Exception {
        UUID id = UUID.randomUUID();
        UUID oldOwner = UUID.randomUUID();
        UUID newOwner = UUID.randomUUID();
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.of(account(id, "Salary", oldOwner)));
        when(accountsRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(id, account(null, "Savings", newOwner));

        AccountAuditEvent event = onlyEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getAccountId()).isEqualTo(id);
        assertThat(event.getPersonId()).isEqualTo(newOwner);
        JsonNode before = json.readTree(event.getBeforeState());
        JsonNode after = json.readTree(event.getAfterState());
        assertThat(before.get("name").asText()).isEqualTo("Salary");
        assertThat(before.get("personId").asText()).isEqualTo(oldOwner.toString());
        assertThat(after.get("name").asText()).isEqualTo("Savings");
        assertThat(after.get("personId").asText()).isEqualTo(newOwner.toString());
    }

    @Test
    void delete_recordsDeletedAccountState() throws Exception {
        UUID id = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        Account existing = account(id, "Salary", personId);
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(accountsRepository).delete(existing);
        verify(accountsRepository).flush();
        AccountAuditEvent event = onlyEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(event.getAccountId()).isEqualTo(id);
        assertThat(event.getPersonId()).isEqualTo(personId);
        assertThat(json.readTree(event.getBeforeState()).get("name").asText()).isEqualTo("Salary");
        assertThat(event.getAfterState()).isNull();
    }

    @Test
    void deleteOfMissingAccount_recordsFailureAndRethrows() {
        UUID id = UUID.randomUUID();
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(AccountNotFoundException.class);

        AccountAuditEvent event = onlyEvent();
        assertThat(event.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(event.getAccountId()).isEqualTo(id);
        assertThat(event.getActor()).isEqualTo("teller-7");
        assertThat(event.getFailureReason()).startsWith("AccountNotFoundException").contains(id.toString());
        verify(accountsRepository, never()).delete(any());
    }

    @Test
    void updateRejectedByDatabase_recordsFailureWithBeforeState() throws Exception {
        UUID id = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.of(account(id, "Salary", personId)));
        when(accountsRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> service.update(id, account(null, "Savings", personId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        AccountAuditEvent event = onlyEvent();
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(json.readTree(event.getBeforeState()).get("name").asText()).isEqualTo("Salary");
        assertThat(json.readTree(event.getAfterState()).get("name").asText()).isEqualTo("Savings");
        assertThat(event.getFailureReason()).startsWith("DataIntegrityViolationException");
    }

    @Test
    void auditStoreFailureDuringFailureRecording_doesNotMaskOriginalError() {
        UUID id = UUID.randomUUID();
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.empty());
        when(auditRepository.save(any())).thenThrow(new IllegalStateException("audit store down"));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(AccountNotFoundException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void auditStoreFailureOnSuccess_failsTheMutation() {
        UUID id = UUID.randomUUID();
        when(accountsRepository.getAccountsById(id)).thenReturn(Optional.of(account(id, "Salary", UUID.randomUUID())));
        when(auditRepository.save(any())).thenThrow(new IllegalStateException("audit store down"));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedCreateWithOversizedName_keepsAttemptedStateValidJson() throws Exception {
        UUID personId = UUID.randomUUID();
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 2100; i++) {
            name.append(i % 2 == 0 ? '"' : 'x');
        }
        when(accountsRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("value too long"));

        assertThatThrownBy(() -> service.save(account(null, name.toString(), personId)))
                .isInstanceOf(DataIntegrityViolationException.class);

        AccountAuditEvent event = onlyEvent();
        assertThat(event.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        JsonNode attempted = json.readTree(event.getAfterState());
        assertThat(attempted.get("personId").asText()).isEqualTo(personId.toString());
        assertThat(attempted.get("name").asText())
                .hasSize(AccountAuditService.MAX_NAME_IN_SNAPSHOT + "...[truncated]".length())
                .endsWith("...[truncated]");
    }
}
