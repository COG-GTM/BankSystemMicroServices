package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.AccountNotFoundException;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");

    BillsRepository billsRepository;
    PaymentsService paymentsService;
    TransferService transferService;
    AccountsService accountsService;
    BillServiceImpl billService;
    Account account;

    @BeforeEach
    void setUp() {
        billsRepository = mock(BillsRepository.class);
        paymentsService = mock(PaymentsService.class);
        transferService = mock(TransferService.class);
        accountsService = mock(AccountsService.class);
        FinancialCurrencyExchange exchange = mock(FinancialCurrencyExchange.class);
        when(exchange.exchange(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        billService = new BillServiceImpl(billsRepository, paymentsService, mock(AdjustmentsService.class), transferService, exchange, accountsService);

        account = new Account("account", LocalDate.now(), UUID.randomUUID());
        account.setId(UUID.randomUUID());
        when(accountsService.getAccount(account.getId())).thenReturn(account);
    }

    @Test
    void saveResolvesAccountFromPath() {
        Bill bill = new Bill(USD, BigDecimal.TEN, false, null);

        billService.save(account.getPersonId(), account.getId(), bill);

        ArgumentCaptor<Bill> saved = ArgumentCaptor.forClass(Bill.class);
        verify(billsRepository).save(saved.capture());
        assertSame(account, saved.getValue().getAccount());
    }

    @Test
    void saveRejectsAccountOfAnotherPerson() {
        assertThrows(AccountNotFoundException.class,
                () -> billService.save(UUID.randomUUID(), account.getId(), new Bill(USD, BigDecimal.TEN, false, null)));
        verify(billsRepository, never()).save(any());
    }

    @Test
    void updateRejectsBillOfAnotherAccount() {
        Account otherAccount = new Account("other", LocalDate.now(), account.getPersonId());
        otherAccount.setId(UUID.randomUUID());
        Bill existing = bill(otherAccount, BigDecimal.TEN);
        when(billsRepository.getBillById(existing.getId())).thenReturn(Optional.of(existing));

        assertThrows(BillNotFoundException.class,
                () -> billService.update(account.getPersonId(), account.getId(), existing.getId(), new Bill(USD, BigDecimal.ONE, true, null)));
        verify(billsRepository, never()).save(any());
    }

    @Test
    void updateKeepsPersistedAccount() {
        Bill existing = bill(account, BigDecimal.TEN);
        when(billsRepository.getBillById(existing.getId())).thenReturn(Optional.of(existing));

        billService.update(account.getPersonId(), account.getId(), existing.getId(), new Bill(USD, BigDecimal.ONE, true, null));

        assertSame(account, existing.getAccount());
        assertTrue(existing.getIsOverdraft());
        verify(billsRepository).save(existing);
    }

    @Test
    void commitPaymentAttachesResolvedBill() {
        Bill bill = bill(account, BigDecimal.TEN);
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", null);

        billService.commitPayment(bill.getId(), payment);

        assertSame(bill, payment.getBill());
        verify(paymentsService).save(payment);
    }

    @Test
    void commitTransferAttachesResolvedBills() {
        Bill source = bill(account, BigDecimal.TEN);
        Bill beneficiary = bill(account, BigDecimal.ZERO);
        when(billsRepository.getBillById(source.getId())).thenReturn(Optional.of(source));
        when(billsRepository.getBillById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        Transfer transfer = new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "transfer", null, null);

        billService.commitTransfer(source.getId(), beneficiary.getId(), transfer);

        assertSame(source, transfer.getSourceBill());
        assertSame(beneficiary, transfer.getBeneficiaryBill());
        verify(transferService).save(transfer);
    }

    private Bill bill(Account owner, BigDecimal amount) {
        Bill bill = new Bill(USD, amount, false, owner);
        bill.setId(UUID.randomUUID());
        return bill;
    }
}
