package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.exceptions.InvalidAmountException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.PaymentsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");

    private BillsRepository billsRepository;
    private PaymentsService paymentsService;
    private AdjustmentsService adjustmentsService;
    private BillServiceImpl billService;
    private Bill bill;

    @BeforeEach
    void setUp() {
        billsRepository = mock(BillsRepository.class);
        paymentsService = mock(PaymentsService.class);
        adjustmentsService = mock(AdjustmentsService.class);
        billService = new BillServiceImpl(billsRepository, paymentsService, adjustmentsService,
                mock(TransferService.class), new JavaMonetaApiCurrencyExchange(mock(Logger.class)));

        bill = new Bill(USD, new BigDecimal("5000.00"), false, new Account());
        bill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
    }

    @Test
    void paymentDebitsExactAmount() {
        Payment payment = new Payment(USD, new BigDecimal("1234.56"), LocalDateTime.now(), "Payment", bill);

        billService.commitPayment(bill.getId(), payment);

        assertEquals(new BigDecimal("3765.44"), bill.getAmount());
        verify(paymentsService).save(payment);
    }

    @Test
    void adjustmentCreditsExactAmount() {
        Adjustment adjustment = new Adjustment(USD, new BigDecimal("0.01"), LocalDateTime.now(), "Adjustment", bill);

        billService.commitAdjustment(bill.getId(), adjustment);

        assertEquals(new BigDecimal("5000.01"), bill.getAmount());
        verify(adjustmentsService).save(adjustment);
    }

    @Test
    void paymentFinerThanMinorUnitIsRejected() {
        Payment payment = new Payment(USD, new BigDecimal("0.001"), LocalDateTime.now(), "Payment", bill);

        assertThrows(InvalidAmountException.class, () -> billService.commitPayment(bill.getId(), payment));

        assertEquals(new BigDecimal("5000.00"), bill.getAmount());
        verify(paymentsService, never()).save(any());
        verify(billsRepository, never()).save(any());
    }

    @Test
    void trailingZerosBeyondMinorUnitAreAccepted() {
        Payment payment = new Payment(USD, new BigDecimal("10.5000"), LocalDateTime.now(), "Payment", bill);

        billService.commitPayment(bill.getId(), payment);

        assertEquals(new BigDecimal("4989.50"), bill.getAmount());
    }
}
