package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");

    private BillsRepository billsRepository;
    private PaymentsService paymentsService;
    private AdjustmentsService adjustmentsService;
    private TransferService transferService;
    private BillServiceImpl billService;

    @BeforeEach
    void setUp() {
        billsRepository = mock(BillsRepository.class);
        paymentsService = mock(PaymentsService.class);
        adjustmentsService = mock(AdjustmentsService.class);
        transferService = mock(TransferService.class);
        FinancialCurrencyExchange exchange = mock(FinancialCurrencyExchange.class);
        when(exchange.exchange(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        billService = new BillServiceImpl(billsRepository, paymentsService, adjustmentsService, transferService, exchange);
    }

    private Bill bill(UUID id, BigDecimal amount) {
        Bill bill = new Bill(USD, amount, false, new Account("account", LocalDate.now(), UUID.randomUUID()));
        bill.setId(id);
        when(billsRepository.findByIdForUpdate(id)).thenReturn(Optional.of(bill));
        return bill;
    }

    @Test
    void moneyMovingMethodsAreTransactionalAndRollBackOnAnyException() throws Exception {
        for (String name : new String[]{"commitAdjustment", "commitPayment", "commitTransfer"}) {
            Transactional tx = java.util.Arrays.stream(BillServiceImpl.class.getMethods())
                    .filter(m -> m.getName().equals(name))
                    .findFirst().get()
                    .getAnnotation(Transactional.class);
            assertThat(tx).as(name + " must be @Transactional").isNotNull();
            assertThat(tx.rollbackFor()).as(name).contains(Exception.class);
        }
    }

    @Test
    void commitTransfer_recordsTheLockedPathBillsNotTheRequestBodyBills() {
        UUID sourceId = UUID.randomUUID();
        UUID beneficiaryId = UUID.randomUUID();
        Bill source = bill(sourceId, BigDecimal.TEN);
        Bill beneficiary = bill(beneficiaryId, BigDecimal.ZERO);

        Bill bodySource = new Bill();
        bodySource.setId(UUID.randomUUID());
        Transfer transfer = new Transfer(USD, new BigDecimal(4), LocalDateTime.now(), "Transfer", bodySource, null);

        billService.commitTransfer(sourceId, beneficiaryId, transfer);

        assertThat(transfer.getSourceBill()).isSameAs(source);
        assertThat(transfer.getBeneficiaryBill()).isSameAs(beneficiary);
        assertThat(source.getAmount()).isEqualByComparingTo("6");
        assertThat(beneficiary.getAmount()).isEqualByComparingTo("4");
        verify(billsRepository).findByIdForUpdate(sourceId);
        verify(billsRepository).findByIdForUpdate(beneficiaryId);
        verify(billsRepository, never()).getBillById(any());
        verify(transferService).save(transfer);
    }

    @Test
    void commitTransfer_locksBillsInIdOrderRegardlessOfDirection() {
        UUID low = new UUID(0, 1);
        UUID high = new UUID(0, 2);
        bill(low, BigDecimal.TEN);
        bill(high, BigDecimal.TEN);

        billService.commitTransfer(high, low, new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "Transfer", null, null));

        org.mockito.InOrder inOrder = inOrder(billsRepository);
        inOrder.verify(billsRepository).findByIdForUpdate(low);
        inOrder.verify(billsRepository).findByIdForUpdate(high);
    }

    @Test
    void commitTransfer_missingBeneficiaryFailsBeforeAnyBalanceChange() {
        UUID sourceId = new UUID(0, 1);
        UUID beneficiaryId = new UUID(0, 2);
        bill(sourceId, BigDecimal.TEN);
        when(billsRepository.findByIdForUpdate(beneficiaryId)).thenReturn(Optional.empty());

        assertThrows(BillNotFoundException.class, () -> billService.commitTransfer(sourceId, beneficiaryId,
                new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "Transfer", null, null)));

        verify(billsRepository, never()).save(any());
        verifyNoInteractions(transferService);
    }

    @Test
    void commitAdjustment_recordsTheLockedPathBill() {
        UUID billId = UUID.randomUUID();
        Bill bill = bill(billId, BigDecimal.ONE);
        Adjustment adjustment = new Adjustment(USD, new BigDecimal(2), LocalDateTime.now(), "Adjustment", new Bill());

        billService.commitAdjustment(billId, adjustment);

        assertThat(adjustment.getBill()).isSameAs(bill);
        assertThat(bill.getAmount()).isEqualByComparingTo("3");
        verify(adjustmentsService).save(adjustment);
    }

    @Test
    void commitPayment_recordsTheLockedPathBill() {
        UUID billId = UUID.randomUUID();
        Bill bill = bill(billId, BigDecimal.TEN);
        Payment payment = new Payment(USD, new BigDecimal(3), LocalDateTime.now(), "Payment", new Bill());

        billService.commitPayment(billId, payment);

        assertThat(payment.getBill()).isSameAs(bill);
        assertThat(bill.getAmount()).isEqualByComparingTo("7");
        verify(paymentsService).save(payment);
    }
}
