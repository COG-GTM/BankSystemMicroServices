package ru.kondrashov.accountservice.services;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Slice;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.exceptions.InvalidPageRequestException;
import ru.kondrashov.accountservice.repositories.BillTransactionHistoryRepository;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.AdjustmentsService;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;
import ru.kondrashov.accountservice.services.interfaces.PaymentsService;
import ru.kondrashov.accountservice.services.interfaces.TransferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillServiceImplTransactionsTest {

    private final BillTransactionHistoryRepository historyRepository = mock(BillTransactionHistoryRepository.class);
    private final PaymentsService paymentsService = mock(PaymentsService.class);
    private final AdjustmentsService adjustmentsService = mock(AdjustmentsService.class);
    private final TransferService transferService = mock(TransferService.class);
    private final BillServiceImpl billService = new BillServiceImpl(
            mock(BillsRepository.class),
            paymentsService,
            adjustmentsService,
            transferService,
            mock(FinancialCurrencyExchange.class),
            historyRepository);

    @Test
    void requestsOneExtraRowToDetectNextPage() {
        UUID billId = UUID.randomUUID();
        when(historyRepository.findHistory(billId, 20L, 11)).thenReturn(rows(11));

        Slice<FinancialTransactionResponseDTO> slice = billService.getFinancialTransactions(billId, 2, 10);

        assertThat(slice.getContent()).hasSize(10);
        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.getNumber()).isEqualTo(2);
        assertThat(slice.getSize()).isEqualTo(10);
        verify(historyRepository).findHistory(billId, 20L, 11);
        verifyNoInteractions(paymentsService, adjustmentsService, transferService);
    }

    @Test
    void lastPageHasNoNext() {
        UUID billId = UUID.randomUUID();
        when(historyRepository.findHistory(billId, 0L, 101)).thenReturn(rows(4));

        Slice<FinancialTransactionResponseDTO> slice = billService.getFinancialTransactions(billId, 0, 100);

        assertThat(slice.getContent()).hasSize(4);
        assertThat(slice.hasNext()).isFalse();
    }

    @Test
    void rejectsOutOfRangePageRequests() {
        UUID billId = UUID.randomUUID();
        assertThatThrownBy(() -> billService.getFinancialTransactions(billId, -1, 10)).isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> billService.getFinancialTransactions(billId, 0, 0)).isInstanceOf(InvalidPageRequestException.class);
        assertThatThrownBy(() -> billService.getFinancialTransactions(billId, 0, 501)).isInstanceOf(InvalidPageRequestException.class);
        verify(historyRepository, never()).findHistory(any(), anyLong(), anyInt());
    }

    private static List<FinancialTransactionResponseDTO> rows(int count) {
        List<FinancialTransactionResponseDTO> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new FinancialTransactionResponseDTO(UUID.randomUUID(), Currency.getInstance("USD"),
                    BigDecimal.ONE, LocalDateTime.of(2021, 1, 1, 0, 0).minusMinutes(i), "t" + i));
        }
        return rows;
    }
}
