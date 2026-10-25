package ru.kondrashov.accountservice.controllers.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.entities.Bill;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialTransactionRequestDTOValidationTest {

    private static final Currency USD = Currency.getInstance("USD");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Map<String, String> violations(Object dto) {
        return validator.validate(dto).stream().collect(Collectors.toMap(
                v -> v.getPropertyPath().toString(),
                ConstraintViolation::getMessage,
                (a, b) -> a + " | " + b));
    }

    private static Stream<FinancialTransactionRequestDTO> all(Currency currency, BigDecimal amount, String message) {
        LocalDateTime now = LocalDateTime.now();
        return Stream.of(
                new PaymentRequestDTO(currency, amount, now, message, new Bill()),
                new AdjustmentRequestDTO(currency, amount, now, message, new Bill()),
                new TransferRequestDTO(currency, amount, now, message, new Bill(), new Bill()));
    }

    @Test
    void validRequestsHaveNoViolations() {
        all(USD, new BigDecimal("0.01"), "ok").forEach(dto -> assertTrue(violations(dto).isEmpty(), dto.toString()));
    }

    @Test
    void missingCurrencyReportsCurrencyMessage() {
        all(null, BigDecimal.TEN, "ok").forEach(dto ->
                assertEquals("Currency should not be null", violations(dto).get("currency"), dto.getClass().getSimpleName()));
    }

    @Test
    void zeroAmountIsRejected() {
        all(USD, BigDecimal.ZERO, "ok").forEach(dto ->
                assertEquals("Amount should be more than zero", violations(dto).get("amount"), dto.getClass().getSimpleName()));
    }

    @Test
    void negativeAmountIsRejected() {
        all(USD, new BigDecimal("-5"), "ok").forEach(dto ->
                assertEquals("Amount should be more than zero", violations(dto).get("amount"), dto.getClass().getSimpleName()));
    }

    @Test
    void missingAmountIsRejected() {
        assertEquals("Amount should not be null", violations(new PaymentRequestDTO()).get("amount"));
        all(USD, null, "ok").forEach(dto ->
                assertEquals("Amount should not be null", violations(dto).get("amount"), dto.getClass().getSimpleName()));
    }

    @Test
    void emptyMessageIsRejected() {
        all(USD, BigDecimal.TEN, "").forEach(dto ->
                assertEquals("Message should not be empty", violations(dto).get("message"), dto.getClass().getSimpleName()));
    }

    @Test
    void missingBillsNameTheField() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals("Bill should not be null",
                violations(new PaymentRequestDTO(USD, BigDecimal.TEN, now, "ok", null)).get("bill"));
        assertEquals("Bill should not be null",
                violations(new AdjustmentRequestDTO(USD, BigDecimal.TEN, now, "ok", null)).get("bill"));
        Map<String, String> transfer = violations(new TransferRequestDTO(USD, BigDecimal.TEN, now, "ok", null, null));
        assertEquals("Source bill should not be null", transfer.get("sourceBill"));
        assertEquals("Beneficiary bill should not be null", transfer.get("beneficiaryBill"));
    }

    @Test
    void responseDTOsCarryNoValidationConstraints() {
        Stream.of(FinancialTransactionResponseDTO.class, PaymentResponseDTO.class,
                AdjustmentResponseDTO.class, TransferResponseDTO.class)
                .forEach(type -> assertTrue(validator.getConstraintsForClass(type).getConstrainedProperties().isEmpty(),
                        type.getSimpleName()));
    }
}
