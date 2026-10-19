package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.AccountNotSettledException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

class AccountsServiceImplCloseTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
    private final UUID personId = UUID.randomUUID();

    private AccountsRepository accountsRepository;
    private BillsRepository billsRepository;
    private AccountsServiceImpl service;
    private Account account;

    @BeforeEach
    void setUp() {
        accountsRepository = mock(AccountsRepository.class);
        billsRepository = mock(BillsRepository.class);
        service = new AccountsServiceImpl(accountsRepository, billsRepository, clock);
        account = new Account("Salary", LocalDate.of(2020, 1, 1), personId);
        account.setId(UUID.randomUUID());
        when(accountsRepository.lockById(account.getId())).thenReturn(Optional.of(account));
    }

    @Test
    void closeSoftClosesSettledAccountAndNeverDeletes() {
        when(billsRepository.getBillsByAccount_Id(account.getId()))
                .thenReturn(Arrays.asList(bill("0"), bill("0.00")));

        service.close(personId, account.getId());

        assertThat(account.getClosedDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        verify(accountsRepository).save(account);
        verify(accountsRepository, never()).delete(any());
        verify(billsRepository, never()).delete(any());
    }

    @Test
    void closeRejectsNonZeroBalance() {
        when(billsRepository.getBillsByAccount_Id(account.getId()))
                .thenReturn(Arrays.asList(bill("0"), bill("-3.10")));

        assertThatThrownBy(() -> service.close(personId, account.getId()))
                .isInstanceOf(AccountNotSettledException.class);
        assertThat(account.getClosedDate()).isNull();
        verify(accountsRepository, never()).save(any());
    }

    @Test
    void closeTreatsMissingBalanceAsUnsettled() {
        Bill bill = bill("0");
        bill.setAmount(null);
        when(billsRepository.getBillsByAccount_Id(account.getId())).thenReturn(Collections.singletonList(bill));

        assertThatThrownBy(() -> service.close(personId, account.getId()))
                .isInstanceOf(AccountNotSettledException.class);
    }

    @Test
    void updateCannotReassignOwner() {
        UUID otherPerson = UUID.randomUUID();

        service.update(personId, account.getId(), new Account("Renamed", LocalDate.of(2020, 1, 1), otherPerson));

        assertThat(account.getPersonId()).isEqualTo(personId);
        assertThat(account.getName()).isEqualTo("Renamed");
        assertThatThrownBy(() -> service.update(otherPerson, account.getId(), new Account("x", LocalDate.now(), otherPerson)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void closeIsIdempotent() {
        account.setClosedDate(LocalDate.of(2025, 5, 5));

        service.close(personId, account.getId());

        assertThat(account.getClosedDate()).isEqualTo(LocalDate.of(2025, 5, 5));
        verify(accountsRepository, never()).save(any());
    }

    @Test
    void closeRejectsAccountOfAnotherPerson() {
        assertThatThrownBy(() -> service.close(UUID.randomUUID(), account.getId()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void closedAccountsAreHiddenAndCannotBeUpdated() {
        when(accountsRepository.getAccountsByPersonIdAndClosedDateIsNull(personId)).thenReturn(Collections.emptyList());
        account.setClosedDate(LocalDate.of(2025, 5, 5));

        assertThat(service.getAccountsByPersonId(personId)).isEmpty();
        assertThatThrownBy(() -> service.update(personId, account.getId(), new Account("x", LocalDate.now(), personId)))
                .isInstanceOf(AccountClosedException.class);
    }

    private Bill bill(String amount) {
        return new Bill(Currency.getInstance("USD"), new BigDecimal(amount), false, account);
    }
}
