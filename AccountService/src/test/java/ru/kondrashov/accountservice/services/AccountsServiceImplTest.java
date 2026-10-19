package ru.kondrashov.accountservice.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.AccountAuditAction;
import ru.kondrashov.accountservice.entities.AccountAuditRecord;
import ru.kondrashov.accountservice.entities.AccountStatus;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.AccountHasNonZeroBalanceException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountAuditRepository;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private AccountsRepository accountsRepository;
    private BillsRepository billsRepository;
    private AccountAuditRepository auditRepository;
    private AccountsServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID personId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountsRepository = mock(AccountsRepository.class);
        billsRepository = mock(BillsRepository.class);
        auditRepository = mock(AccountAuditRepository.class);
        service = new AccountsServiceImpl(accountsRepository, billsRepository, auditRepository, objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        personId = UUID.randomUUID();
        account = new Account("Salary", LocalDate.of(2021, 4, 20), personId);
        account.setId(UUID.randomUUID());
        when(accountsRepository.getAccountsByIdAndStatus(account.getId(), AccountStatus.OPEN)).thenReturn(Optional.of(account));
    }

    private Bill bill(String amount) {
        Bill bill = new Bill(Currency.getInstance("USD"), new BigDecimal(amount), false, account);
        bill.setId(UUID.randomUUID());
        return bill;
    }

    private AccountAuditRecord capturedAudit() {
        ArgumentCaptor<AccountAuditRecord> captor = ArgumentCaptor.forClass(AccountAuditRecord.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void closeSoftClosesAccountWithZeroBillsAndWritesAudit() throws Exception {
        Bill zero = bill("0.00");
        when(billsRepository.getBillsByAccount_Id(account.getId())).thenReturn(Collections.singletonList(zero));

        service.close(personId, account.getId(), "teller-7");

        verify(accountsRepository, never()).delete(any());
        verify(accountsRepository, never()).deleteById(any());
        verify(billsRepository, never()).delete(any());
        verify(accountsRepository).save(account);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getClosedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        AccountAuditRecord record = capturedAudit();
        assertThat(record.getAccountId()).isEqualTo(account.getId());
        assertThat(record.getAction()).isEqualTo(AccountAuditAction.CLOSED);
        assertThat(record.getActor()).isEqualTo("teller-7");
        assertThat(record.getOccurredAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        JsonNode before = objectMapper.readTree(record.getBeforeState());
        JsonNode after = objectMapper.readTree(record.getAfterState());
        assertThat(before.get("status").asText()).isEqualTo("OPEN");
        assertThat(after.get("status").asText()).isEqualTo("CLOSED");
        assertThat(before.get("bills").get(0).get("id").asText()).isEqualTo(zero.getId().toString());
    }

    @Test
    void closeRefusesAccountWithNonZeroBill() {
        Bill funded = bill("10.00");
        when(billsRepository.getBillsByAccount_Id(account.getId())).thenReturn(Arrays.asList(bill("0"), funded));

        assertThatThrownBy(() -> service.close(personId, account.getId(), "teller-7"))
                .isInstanceOf(AccountHasNonZeroBalanceException.class)
                .hasMessageContaining(funded.getId().toString());

        assertThat(account.getStatus()).isEqualTo(AccountStatus.OPEN);
        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void closeRefusesOverdrawnBill() {
        when(billsRepository.getBillsByAccount_Id(account.getId())).thenReturn(Collections.singletonList(bill("-5")));

        assertThatThrownBy(() -> service.close(personId, account.getId(), "teller-7"))
                .isInstanceOf(AccountHasNonZeroBalanceException.class);
    }

    @Test
    void closeRejectsAccountOfAnotherPerson() {
        assertThatThrownBy(() -> service.close(UUID.randomUUID(), account.getId(), "teller-7"))
                .isInstanceOf(AccountNotFoundException.class);
        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(auditRepository);
    }

    @Test
    void closedAccountIsNoLongerVisible() {
        UUID closedId = UUID.randomUUID();
        when(accountsRepository.getAccountsByIdAndStatus(closedId, AccountStatus.OPEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccount(closedId)).isInstanceOf(AccountNotFoundException.class);
        service.getAccountsByPersonId(personId);
        verify(accountsRepository).getAccountsByPersonIdAndStatus(personId, AccountStatus.OPEN);
    }

    @Test
    void ownershipChangeIsAuditedWithBeforeAndAfter() throws Exception {
        UUID newOwner = UUID.randomUUID();

        service.update(personId, account.getId(), new Account("Salary", account.getCreationDate(), newOwner), "teller-7");

        assertThat(account.getPersonId()).isEqualTo(newOwner);
        AccountAuditRecord record = capturedAudit();
        assertThat(record.getAction()).isEqualTo(AccountAuditAction.OWNERSHIP_CHANGED);
        assertThat(objectMapper.readTree(record.getBeforeState()).get("personId").asText()).isEqualTo(personId.toString());
        assertThat(objectMapper.readTree(record.getAfterState()).get("personId").asText()).isEqualTo(newOwner.toString());
    }

    @Test
    void plainUpdateIsAuditedAsUpdated() {
        service.update(personId, account.getId(), new Account("Savings", account.getCreationDate(), personId), null);

        AccountAuditRecord record = capturedAudit();
        assertThat(record.getAction()).isEqualTo(AccountAuditAction.UPDATED);
        assertThat(record.getActor()).isEqualTo("unknown");
    }

    @Test
    void updateRejectsAccountOfAnotherPerson() {
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), account.getId(),
                new Account("x", account.getCreationDate(), UUID.randomUUID()), "attacker"))
                .isInstanceOf(AccountNotFoundException.class);
        assertThat(account.getPersonId()).isEqualTo(personId);
        verifyNoInteractions(auditRepository);
    }
}
