package ru.kondrashov.personservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.kondrashov.personservice.entities.Person;
import ru.kondrashov.personservice.repositories.PeopleRepository;
import ru.kondrashov.personservice.services.PeopleService;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class PeopleServiceTransactionTest {

    @MockBean
    private PeopleRepository peopleRepository;

    @Autowired
    private PeopleService peopleService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<TxState> observed = new ArrayList<>();

    private TxState record() {
        TxState state = new TxState(
                TransactionSynchronizationManager.isActualTransactionActive(),
                TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                TransactionSynchronizationManager.getResource(entityManagerFactory));
        observed.add(state);
        return state;
    }

    private Person person(UUID id) {
        return new Person(id, "Mike", "Tyson", LocalDate.of(1966, 6, 30), "+7 1234859520", "miketyson@mail.com");
    }

    private void stubLookup(UUID id) {
        when(peopleRepository.getPersonById(id)).thenAnswer(invocation -> {
            record();
            return Optional.of(person(id));
        });
    }

    @Test
    void updateCalledDirectlyReadsAndWritesInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubLookup(id);
        when(peopleRepository.save(any(Person.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        peopleService.update(id, person(null));

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void deleteCalledDirectlyReadsAndDeletesInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubLookup(id);
        doAnswer(invocation -> {
            record();
            return null;
        }).when(peopleRepository).delete(any(Person.class));

        peopleService.delete(id);

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void saveCalledDirectlyRunsInReadWriteTransaction() {
        when(peopleRepository.save(any(Person.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        peopleService.save(person(null));

        assertSingleReadWriteTransaction(1);
    }

    @Test
    void gettersRunInReadOnlyTransactions() {
        UUID id = UUID.randomUUID();
        stubLookup(id);
        when(peopleRepository.findAll()).thenAnswer(invocation -> {
            record();
            return new ArrayList<Person>();
        });

        peopleService.get(id);
        peopleService.getAll();

        assertThat(observed).hasSize(2);
        assertThat(observed).allSatisfy(state -> {
            assertThat(state.active).isTrue();
            assertThat(state.readOnly).isTrue();
        });
    }

    @Test
    void updateRollsBackWhenACheckedExceptionIsThrown() {
        UUID id = UUID.randomUUID();
        stubLookup(id);
        AtomicInteger completionStatus = new AtomicInteger(-1);
        when(peopleRepository.save(any(Person.class))).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    completionStatus.set(status);
                }
            });
            throw new Exception("simulated checked failure");
        });

        assertThatThrownBy(() -> peopleService.update(id, person(null))).hasRootCauseMessage("simulated checked failure");
        assertThat(completionStatus.get()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void assertSingleReadWriteTransaction(int expectedCalls) {
        assertThat(observed).hasSize(expectedCalls);
        assertThat(observed).allSatisfy(state -> {
            assertThat(state.active).isTrue();
            assertThat(state.readOnly).isFalse();
            assertThat(state.entityManagerHolder).isNotNull();
        });
        assertThat(observed.stream().map(state -> state.entityManagerHolder).distinct()).hasSize(1);
    }

    private static final class TxState {
        private final boolean active;
        private final boolean readOnly;
        private final Object entityManagerHolder;

        private TxState(boolean active, boolean readOnly, Object entityManagerHolder) {
            this.active = active;
            this.readOnly = readOnly;
            this.entityManagerHolder = entityManagerHolder;
        }
    }
}
