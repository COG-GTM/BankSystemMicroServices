package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountsServiceImplTest {

    private final AccountsRepository repository = mock(AccountsRepository.class);
    private final AccountsServiceImpl service = new AccountsServiceImpl(repository);

    @Test
    void updateOfAnotherPersonsAccountThrowsAndDoesNotSave() {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(repository.getAccountByIdAndPersonId(accountId, personId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(personId, accountId, new Account("x", LocalDate.now(), personId)))
                .isInstanceOf(AccountNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteOfAnotherPersonsAccountThrowsAndDoesNotDelete() {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        when(repository.getAccountByIdAndPersonId(accountId, personId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(personId, accountId))
                .isInstanceOf(AccountNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void updateNeverReassignsTheAccountToAnotherPerson() {
        UUID owner = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Account stored = new Account("old", LocalDate.of(2021, 1, 1), owner);
        when(repository.getAccountByIdAndPersonId(accountId, owner)).thenReturn(Optional.of(stored));

        service.update(owner, accountId, new Account("new", LocalDate.of(2021, 2, 2), UUID.randomUUID()));

        verify(repository).save(stored);
        assertThat(stored.getName()).isEqualTo("new");
        assertThat(stored.getCreationDate()).isEqualTo(LocalDate.of(2021, 2, 2));
        assertThat(stored.getPersonId()).isEqualTo(owner);
    }
}
