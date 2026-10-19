package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.BillUpdateNotAllowedException;
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

@ExtendWith(MockitoExtension.class)
class BillServiceImplTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

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

    @InjectMocks
    private BillServiceImpl billService;

    private Account account;
    private Bill bill;

    @BeforeEach
    void setUp() {
        account = account();
        bill = bill(USD, new BigDecimal("100.00"), false, account);
    }

    @Test
    void update_rejectsAmountChange_andLeavesBalanceUntouched() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));

        Bill request = new Bill(USD, new BigDecimal("1000000"), false, account);

        assertThatThrownBy(() -> billService.update(bill.getId(), request))
                .isInstanceOf(BillUpdateNotAllowedException.class)
                .hasMessageContaining("adjustment");

        assertThat(bill.getAmount()).isEqualByComparingTo("100.00");
        verify(billsRepository, never()).save(any());
        verifyNoInteractions(adjustmentsService, paymentsService, transferService, currencyConversion);
    }

    @Test
    void update_rejectsMovingBillToAnotherAccount() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));

        Bill request = new Bill(USD, null, false, account());

        assertThatThrownBy(() -> billService.update(bill.getId(), request))
                .isInstanceOf(BillUpdateNotAllowedException.class)
                .hasMessageContaining("another account");

        assertThat(bill.getAccount()).isSameAs(account);
        verify(billsRepository, never()).save(any());
    }

    @Test
    void update_changesOverdraftOnly_whenAmountOmitted() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));

        billService.update(bill.getId(), new Bill(USD, null, true, account));

        assertThat(bill.getIsOverdraft()).isTrue();
        assertThat(bill.getAmount()).isEqualByComparingTo("100.00");
        assertThat(bill.getCurrency()).isEqualTo(USD);
        verify(billsRepository).save(bill);
        verifyNoInteractions(currencyConversion);
    }

    @Test
    void update_acceptsUnchangedAmountEcho() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));

        billService.update(bill.getId(), new Bill(USD, new BigDecimal("100"), true, account));

        assertThat(bill.getAmount()).isEqualByComparingTo("100.00");
        verify(billsRepository).save(bill);
    }

    @Test
    void update_currencyChange_convertsStoredBalance_notRequestAmount() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(currencyConversion.exchange(USD, EUR, bill.getAmount())).thenReturn(new BigDecimal("90.00"));

        billService.update(bill.getId(), new Bill(EUR, new BigDecimal("100.00"), false, account));

        assertThat(bill.getCurrency()).isEqualTo(EUR);
        assertThat(bill.getAmount()).isEqualByComparingTo("90.00");
        verify(currencyConversion).exchange(USD, EUR, new BigDecimal("100.00"));
        verify(billsRepository).save(bill);
    }

    @Test
    void commitAdjustment_recordsLedgerEntryAgainstAdjustedBill() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(currencyConversion.exchange(USD, USD, new BigDecimal("5"))).thenReturn(new BigDecimal("5"));
        Bill otherBill = bill(USD, BigDecimal.ZERO, false, account);
        Adjustment adjustment = new Adjustment(USD, new BigDecimal("5"), LocalDateTime.now(), "Correction", otherBill);

        billService.commitAdjustment(bill.getId(), adjustment);

        assertThat(bill.getAmount()).isEqualByComparingTo("105.00");
        assertThat(adjustment.getBill()).isSameAs(bill);
        verify(adjustmentsService).save(adjustment);
    }

    @Test
    void commitPayment_recordsLedgerEntryAgainstDebitedBill() {
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(currencyConversion.exchange(USD, USD, new BigDecimal("5"))).thenReturn(new BigDecimal("5"));
        Payment payment = new Payment(USD, new BigDecimal("5"), LocalDateTime.now(), "Order", bill(USD, BigDecimal.ZERO, false, account));

        billService.commitPayment(bill.getId(), payment);

        assertThat(bill.getAmount()).isEqualByComparingTo("95.00");
        assertThat(payment.getBill()).isSameAs(bill);
        verify(paymentsService).save(payment);
    }

    @Test
    void commitTransfer_recordsLedgerEntryAgainstBothBills() {
        Bill beneficiary = bill(USD, BigDecimal.ZERO, false, account);
        when(billsRepository.getBillById(bill.getId())).thenReturn(Optional.of(bill));
        when(billsRepository.getBillById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(currencyConversion.exchange(USD, USD, new BigDecimal("5"))).thenReturn(new BigDecimal("5"));
        Bill unrelated = bill(USD, BigDecimal.ZERO, false, account);
        Transfer transfer = new Transfer(USD, new BigDecimal("5"), LocalDateTime.now(), "Move", unrelated, unrelated);

        billService.commitTransfer(bill.getId(), beneficiary.getId(), transfer);

        assertThat(bill.getAmount()).isEqualByComparingTo("95.00");
        assertThat(beneficiary.getAmount()).isEqualByComparingTo("5");
        assertThat(transfer.getSourceBill()).isSameAs(bill);
        assertThat(transfer.getBeneficiaryBill()).isSameAs(beneficiary);
        verify(transferService).save(transfer);
    }

    private static Account account() {
        Account account = new Account("account", LocalDate.now(), UUID.randomUUID());
        account.setId(UUID.randomUUID());
        return account;
    }

    private static Bill bill(Currency currency, BigDecimal amount, boolean overdraft, Account account) {
        Bill bill = new Bill(currency, amount, overdraft, account);
        bill.setId(UUID.randomUUID());
        return bill;
    }
}
