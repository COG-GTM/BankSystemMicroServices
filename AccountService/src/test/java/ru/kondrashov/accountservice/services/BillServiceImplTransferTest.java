package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.SameBillTransferException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;
import ru.kondrashov.accountservice.services.interfaces.PaymentsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillServiceImplTransferTest {

    private final BillsRepository billsRepository = mock(BillsRepository.class);
    private final TransferService transferService = mock(TransferService.class);
    private final FinancialCurrencyExchange exchange = mock(FinancialCurrencyExchange.class);
    private final BillServiceImpl billService = new BillServiceImpl(
            billsRepository, mock(PaymentsService.class), mock(AdjustmentsService.class), transferService, exchange);

    @Test
    void transferIsRecordedAgainstPathBillsWhenBodyOmitsThem() {
        Bill source = bill(new BigDecimal("100"));
        Bill beneficiary = bill(BigDecimal.ZERO);
        when(billsRepository.getBillById(source.getId())).thenReturn(Optional.of(source));
        when(billsRepository.getBillById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(exchange.exchange(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));

        Transfer transfer = new Transfer(Currency.getInstance("USD"), new BigDecimal("40"), LocalDateTime.now(), "Rent", null, null);
        billService.commitTransfer(source.getId(), beneficiary.getId(), transfer);

        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferService).save(saved.capture());
        assertThat(saved.getValue().getSourceBill()).isSameAs(source);
        assertThat(saved.getValue().getBeneficiaryBill()).isSameAs(beneficiary);
        assertThat(source.getAmount()).isEqualByComparingTo("60");
        assertThat(beneficiary.getAmount()).isEqualByComparingTo("40");
    }

    @Test
    void bodyBillsCannotRedirectTheRecordedTransfer() {
        Bill source = bill(new BigDecimal("100"));
        Bill beneficiary = bill(BigDecimal.ZERO);
        when(billsRepository.getBillById(source.getId())).thenReturn(Optional.of(source));
        when(billsRepository.getBillById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));
        when(exchange.exchange(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));

        Transfer transfer = new Transfer(Currency.getInstance("USD"), new BigDecimal("40"), LocalDateTime.now(), "Rent",
                bill(BigDecimal.ZERO), bill(BigDecimal.ZERO));
        billService.commitTransfer(source.getId(), beneficiary.getId(), transfer);

        ArgumentCaptor<Transfer> saved = ArgumentCaptor.forClass(Transfer.class);
        verify(transferService).save(saved.capture());
        assertThat(saved.getValue().getSourceBill().getId()).isEqualTo(source.getId());
        assertThat(saved.getValue().getBeneficiaryBill().getId()).isEqualTo(beneficiary.getId());
    }

    @Test
    void transferToTheSameBillIsRejectedWithoutTouchingBalances() {
        Bill source = bill(new BigDecimal("100"));
        Transfer transfer = new Transfer(Currency.getInstance("USD"), new BigDecimal("40"), LocalDateTime.now(), "Rent", null, null);

        assertThatThrownBy(() -> billService.commitTransfer(source.getId(), source.getId(), transfer))
                .isInstanceOf(SameBillTransferException.class);
        verifyNoInteractions(billsRepository, transferService);
        assertThat(source.getAmount()).isEqualByComparingTo("100");
    }

    private Bill bill(BigDecimal amount) {
        Bill bill = new Bill();
        bill.setId(UUID.randomUUID());
        bill.setCurrency(Currency.getInstance("USD"));
        bill.setAmount(amount);
        bill.setIsOverdraft(false);
        return bill;
    }
}
