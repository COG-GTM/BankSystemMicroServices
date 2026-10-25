package ru.kondrashov.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;

import javax.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
public class ServiceTransactionBoundaryTest {

    @MockBean
    private AccountsRepository accountsRepository;

    @MockBean
    private BillsRepository billsRepository;

    @MockBean
    private FinancialCurrencyExchange currencyExchange;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private BillsService billsService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final List<TxState> observed = new ArrayList<>();

    private Object record() {
        observed.add(new TxState(
                TransactionSynchronizationManager.isActualTransactionActive(),
                TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                TransactionSynchronizationManager.getResource(entityManagerFactory)));
        return null;
    }

    private Account account(UUID id) {
        Account account = new Account("Salary", LocalDate.now(), UUID.randomUUID());
        account.setId(id);
        return account;
    }

    private Bill bill(UUID id) {
        Bill bill = new Bill(Currency.getInstance("USD"), BigDecimal.TEN, false, account(UUID.randomUUID()));
        bill.setId(id);
        return bill;
    }

    private void stubAccountLookup(UUID id) {
        when(accountsRepository.getAccountsById(id)).thenAnswer(invocation -> {
            record();
            return Optional.of(account(id));
        });
    }

    private void stubBillLookup(UUID id) {
        when(billsRepository.getBillById(id)).thenAnswer(invocation -> {
            record();
            return Optional.of(bill(id));
        });
    }

    @Test
    void accountUpdateCalledDirectlyRunsInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubAccountLookup(id);
        when(accountsRepository.save(any(Account.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        accountsService.update(id, account(null));

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void accountDeleteCalledDirectlyRunsInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubAccountLookup(id);
        doAnswer(invocation -> record()).when(accountsRepository).delete(any(Account.class));

        accountsService.delete(id);

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void accountSaveCalledDirectlyRunsInReadWriteTransaction() {
        when(accountsRepository.save(any(Account.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        accountsService.save(account(null));

        assertSingleReadWriteTransaction(1);
    }

    @Test
    void billUpdateCalledDirectlyRunsInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubBillLookup(id);
        when(currencyExchange.exchange(any(), any(), any())).thenReturn(BigDecimal.ONE);
        when(billsRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        billsService.update(id, bill(null));

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void billDeleteCalledDirectlyRunsInOneReadWriteTransaction() {
        UUID id = UUID.randomUUID();
        stubBillLookup(id);
        doAnswer(invocation -> record()).when(billsRepository).delete(any(Bill.class));

        billsService.delete(id);

        assertSingleReadWriteTransaction(2);
    }

    @Test
    void billSaveCalledDirectlyRunsInReadWriteTransaction() {
        when(billsRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            record();
            return invocation.getArgument(0);
        });

        billsService.save(bill(null));

        assertSingleReadWriteTransaction(1);
    }

    @Test
    void gettersRunInReadOnlyTransactions() {
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();
        stubAccountLookup(accountId);
        stubBillLookup(billId);
        when(accountsRepository.findAll()).thenAnswer(invocation -> {
            record();
            return new ArrayList<Account>();
        });
        when(accountsRepository.getAccountsByPersonId(any())).thenAnswer(invocation -> {
            record();
            return new ArrayList<Account>();
        });
        when(billsRepository.findAll()).thenAnswer(invocation -> {
            record();
            return new ArrayList<Bill>();
        });
        when(billsRepository.getBillsByAccount_Id(any())).thenAnswer(invocation -> {
            record();
            return new ArrayList<Bill>();
        });

        accountsService.getAccount(accountId);
        accountsService.getAccounts();
        accountsService.getAccountsByPersonId(UUID.randomUUID());
        billsService.getBill(billId);
        billsService.getBills();
        billsService.getBillsByAccountId(accountId);

        assertThat(observed).hasSize(6);
        assertThat(observed).allSatisfy(state -> {
            assertThat(state.active).isTrue();
            assertThat(state.readOnly).isTrue();
        });
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
