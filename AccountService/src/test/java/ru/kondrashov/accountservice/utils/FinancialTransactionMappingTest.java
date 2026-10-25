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

class FinancialTransactionMappingTest {

    private static final Currency CURRENCY = Currency.getInstance("EUR");
    private static final BigDecimal AMOUNT = new BigDecimal("12.34");
    private static final LocalDateTime TIME = LocalDateTime.of(2021, 3, 4, 5, 6, 7);

    @Test
    void mapsAllBaseFields() {
        Payment payment = withId(new Payment(CURRENCY, AMOUNT, TIME, "Payment", new Bill()));

        FinancialTransactionResponseDTO financialTransactionResponseDTO =
                FinancialTransactionMapping.mapToFinancialTransactionResponseDTO(payment);

        assertEquals(payment.getId(),       financialTransactionResponseDTO.getId());
        assertEquals(payment.getCurrency(), financialTransactionResponseDTO.getCurrency());
        assertEquals(payment.getAmount(),   financialTransactionResponseDTO.getAmount());
        assertEquals(payment.getTime(),     financialTransactionResponseDTO.getTime());
        assertEquals(payment.getMessage(),  financialTransactionResponseDTO.getMessage());
    }

    @Test
    void adjustmentMappingDelegatesToSharedMapping() {
        Adjustment adjustment = withId(new Adjustment(CURRENCY, AMOUNT, TIME, "Adjustment", new Bill()));

        assertEquals(expected(adjustment), new AdjustmentMappingImpl().mapToFinancialTransactionResponseDTO(adjustment));
    }

    @Test
    void paymentsMappingDelegatesToSharedMapping() {
        Payment payment = withId(new Payment(CURRENCY, AMOUNT, TIME, "Payment", new Bill()));

        assertEquals(expected(payment), new PaymentsMappingImpl().mapToFinancialTransactionResponseDTO(payment));
    }

    @Test
    void transferMappingDelegatesToSharedMapping() {
        Transfer transfer = withId(new Transfer(CURRENCY, AMOUNT, TIME, "Transfer", new Bill(), new Bill()));

        assertEquals(expected(transfer), new TransferMappingImpl().mapToFinancialTransactionResponseDTO(transfer));
    }

    private static <T extends FinancialTransaction> T withId(T financialTransaction) {
        financialTransaction.setId(UUID.randomUUID());
        return financialTransaction;
    }

    private static FinancialTransactionResponseDTO expected(FinancialTransaction financialTransaction) {
        return new FinancialTransactionResponseDTO(
                financialTransaction.getId(),
                financialTransaction.getCurrency(),
                financialTransaction.getAmount(),
                financialTransaction.getTime(),
                financialTransaction.getMessage()
        );
    }
}
