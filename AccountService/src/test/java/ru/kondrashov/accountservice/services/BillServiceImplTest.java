package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.AccountStatus;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.exceptions.AccountClosedException;
import ru.kondrashov.accountservice.repositories.AccountsRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;
import ru.kondrashov.accountservice.services.interfaces.PaymentsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");

    private BillsRepository billsRepository;
    private AccountsRepository accountsRepository;
    private AdjustmentsService adjustmentsService;
    private FinancialCurrencyExchange currencyExchange;
    private BillServiceImpl service;

    private Account closed;
    private Account open;
    private Bill retainedBill;

    @BeforeEach
    void setUp() {
        billsRepository = mock(BillsRepository.class);
        accountsRepository = mock(AccountsRepository.class);
        adjustmentsService = mock(AdjustmentsService.class);
        currencyExchange = mock(FinancialCurrencyExchange.class);
        when(currencyExchange.exchange(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        service = new BillServiceImpl(billsRepository, accountsRepository, mock(PaymentsService.class),
                adjustmentsService, mock(TransferService.class), currencyExchange);

        closed = account(AccountStatus.CLOSED);
        open = account(AccountStatus.OPEN);
        retainedBill = new Bill(USD, BigDecimal.ZERO, false, closed);
        retainedBill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(retainedBill.getId())).thenReturn(Optional.of(retainedBill));
        when(billsRepository.findAccountIdByBillId(retainedBill.getId())).thenReturn(Optional.of(closed.getId()));
    }

    private Account account(AccountStatus status) {
        Account account = new Account("Salary", LocalDate.of(2021, 4, 20), UUID.randomUUID());
        account.setId(UUID.randomUUID());
        account.setStatus(status);
        when(accountsRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void billOfClosedAccountCannotBeMovedToOpenAccount() {
        Bill change = new Bill(USD, new BigDecimal("100"), false, open);

        assertThatThrownBy(() -> service.update(retainedBill.getId(), change))
                .isInstanceOf(AccountClosedException.class);
        assertThat(retainedBill.getAccount()).isEqualTo(closed);
        assertThat(retainedBill.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(billsRepository, never()).save(any());
    }

    @Test
    void billOfClosedAccountCannotBeDeleted() {
        assertThatThrownBy(() -> service.delete(retainedBill.getId()))
                .isInstanceOf(AccountClosedException.class);
        verify(billsRepository, never()).delete(any());
    }

    @Test
    void newBillCannotBeAddedToClosedAccount() {
        assertThatThrownBy(() -> service.save(new Bill(USD, BigDecimal.ZERO, false, closed)))
                .isInstanceOf(AccountClosedException.class);
        verify(billsRepository, never()).save(any());
    }

    @Test
    void adjustmentOnClosedAccountIsRejected() {
        Adjustment adjustment = new Adjustment(USD, BigDecimal.TEN, LocalDateTime.now(), "credit", retainedBill);

        assertThatThrownBy(() -> service.commitAdjustment(retainedBill.getId(), adjustment))
                .isInstanceOf(AccountClosedException.class);
        verifyNoInteractions(adjustmentsService);
        verify(billsRepository, never()).save(any());
    }

    @Test
    void ownerIsRecheckedAfterLockingWhenBillMovedConcurrently() {
        Bill movedBill = new Bill(USD, BigDecimal.ZERO, false, closed);
        movedBill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(movedBill.getId())).thenReturn(Optional.of(movedBill));
        when(billsRepository.findAccountIdByBillId(movedBill.getId()))
                .thenReturn(Optional.of(open.getId()), Optional.of(closed.getId()));
        Adjustment adjustment = new Adjustment(USD, BigDecimal.TEN, LocalDateTime.now(), "credit", movedBill);

        assertThatThrownBy(() -> service.commitAdjustment(movedBill.getId(), adjustment))
                .isInstanceOf(AccountClosedException.class);
        verify(accountsRepository).findByIdForUpdate(open.getId());
        verify(accountsRepository).findByIdForUpdate(closed.getId());
        verifyNoInteractions(adjustmentsService);
        verify(billsRepository, never()).save(any());
    }

    @Test
    void adjustmentProceedsOnceOwnerIsStableAndOpen() {
        Bill bill = new Bill(USD, BigDecimal.ZERO, false, open);
        bill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(billsRepository.findAccountIdByBillId(bill.getId())).thenReturn(Optional.of(open.getId()));
        Adjustment adjustment = new Adjustment(USD, BigDecimal.TEN, LocalDateTime.now(), "credit", bill);

        service.commitAdjustment(bill.getId(), adjustment);

        verify(accountsRepository, times(1)).findByIdForUpdate(open.getId());
        verify(billsRepository, times(2)).findAccountIdByBillId(bill.getId());
        verify(adjustmentsService).save(adjustment);
    }

    @Test
    void billMovedTwiceIsMutatedOnceFinalOwnerIsLocked() {
        Account first = account(AccountStatus.OPEN);
        Account second = account(AccountStatus.OPEN);
        Bill bill = new Bill(USD, BigDecimal.ZERO, false, open);
        bill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(billsRepository.findAccountIdByBillId(bill.getId()))
                .thenReturn(Optional.of(first.getId()), Optional.of(second.getId()), Optional.of(open.getId()));
        Adjustment adjustment = new Adjustment(USD, BigDecimal.TEN, LocalDateTime.now(), "credit", bill);

        service.commitAdjustment(bill.getId(), adjustment);

        verify(accountsRepository).findByIdForUpdate(open.getId());
        verify(billsRepository, times(4)).findAccountIdByBillId(bill.getId());
        verify(adjustmentsService).save(adjustment);
    }
}
