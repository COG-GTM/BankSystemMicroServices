package ru.kondrashov.accountservice.utils;

import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.FinancialTransaction;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinancialTransactionMappingImplTest {

    FinancialTransactionMappingImpl financialTransactionMapping = new FinancialTransactionMappingImpl();

    @Test
    void mapsAdjustment() {
        assertMapped(new Adjustment(Currency.getInstance("USD"), BigDecimal.TEN, LocalDateTime.now(), "Adjustment", new Bill()));
    }

    @Test
    void mapsPayment() {
        assertMapped(new Payment(Currency.getInstance("EUR"), new BigDecimal("12.34"), LocalDateTime.now(), "Payment", new Bill()));
    }

    @Test
    void mapsTransfer() {
        assertMapped(new Transfer(Currency.getInstance("RUB"), BigDecimal.ONE, LocalDateTime.now(), "Transfer", new Bill(), new Bill()));
    }

    private void assertMapped(FinancialTransaction financialTransaction) {
        financialTransaction.setId(UUID.randomUUID());

        FinancialTransactionResponseDTO financialTransactionResponseDTO = financialTransactionMapping.mapToFinancialTransactionResponseDTO(financialTransaction);

        assertEquals(financialTransaction.getId(),       financialTransactionResponseDTO.getId());
        assertEquals(financialTransaction.getCurrency(), financialTransactionResponseDTO.getCurrency());
        assertEquals(financialTransaction.getAmount(),   financialTransactionResponseDTO.getAmount());
        assertEquals(financialTransaction.getTime(),     financialTransactionResponseDTO.getTime());
        assertEquals(financialTransaction.getMessage(),  financialTransactionResponseDTO.getMessage());
    }
}
