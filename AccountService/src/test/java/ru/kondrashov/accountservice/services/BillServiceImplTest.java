package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Mock
    private BillsRepository billsRepository;
    @Mock
    private PaymentsService paymentsService;
    @Mock
    private AdjustmentsService adjustmentsService;
    @Mock
    private TransferService transferService;
    @Mock
    private FinancialCurrencyExchange currencyConversion;

    private BillServiceImpl billService;

    @BeforeEach
    void setUp() {
        billService = new BillServiceImpl(billsRepository, paymentsService, adjustmentsService, transferService, currencyConversion);
        when(currencyConversion.exchange(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
    }

    private Bill storedBill(BigDecimal amount) {
        Bill bill = new Bill(USD, amount, false, new Account("account", LocalDate.now(), UUID.randomUUID()));
        bill.setId(UUID.randomUUID());
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        return bill;
    }

    private Bill otherBill() {
        Bill bill = new Bill(USD, BigDecimal.ZERO, false, new Account("other", LocalDate.now(), UUID.randomUUID()));
        bill.setId(UUID.randomUUID());
        return bill;
    }

    @Test
    void commitPayment_recordsTheDebitedBillNotTheBodyBill() {
        Bill debited = storedBill(BigDecimal.TEN);
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", otherBill());

        billService.commitPayment(debited.getId(), payment);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentsService).save(saved.capture());
        assertThat(saved.getValue().getBill()).isSameAs(debited);
        assertThat(debited.getAmount()).isEqualByComparingTo("9");
    }

    @Test
    void commitPayment_withoutBodyBill_recordsTheDebitedBill() {
        Bill debited = storedBill(BigDecimal.TEN);
        Payment payment = new Payment(USD, BigDecimal.ONE, LocalDateTime.now(), "payment", null);

        billService.commitPayment(debited.getId(), payment);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentsService).save(saved.capture());
        assertThat(saved.getValue().getBill()).isSameAs(debited);
    }

    @Test
    void commitAdjustment_recordsTheCreditedBillNotTheBodyBill() {
        Bill credited = storedBill(BigDecimal.TEN);
        Adjustment adjustment = new Adjustment(USD, BigDecimal.ONE, LocalDateTime.now(), "adjustment", otherBill());

        billService.commitAdjustment(credited.getId(), adjustment);

        ArgumentCaptor<Adjustment> saved = ArgumentCaptor.forClass(Adjustment.class);
        verify(adjustmentsService).save(saved.capture());
        assertThat(saved.getValue().getBill()).isSameAs(credited);
        assertThat(credited.getAmount()).isEqualByComparingTo("11");
    }

    @Test
    void commitTransfer_recordsTheDebitedAndCreditedBillsNotTheBodyBills() {
        Bill source = storedBill(BigDecimal.TEN);
        Bill beneficiary = storedBill(BigDecimal.ZERO);
        Transfer transfer = new Transfer(USD, BigDecimal.ONE, LocalDateTime.now(), "transfer", otherBill(), otherBill());

        billService.commitTransfer(source.getId(), beneficiary.getId(), transfer);

        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferService).save(saved.capture());
        assertThat(saved.getValue().getSourceBill()).isSameAs(source);
        assertThat(saved.getValue().getBeneficiaryBill()).isSameAs(beneficiary);
        assertThat(source.getAmount()).isEqualByComparingTo("9");
        assertThat(beneficiary.getAmount()).isEqualByComparingTo("1");
    }
}
