package ru.kondrashov.accountservice.controllers.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialTransactionRequestDTOValidationTest {

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

    static Stream<Arguments> requestTypes() {
        return Stream.of(
                Arguments.of("payment", (Supplier<FinancialTransactionRequestDTO>) PaymentRequestDTO::new),
                Arguments.of("adjustment", (Supplier<FinancialTransactionRequestDTO>) AdjustmentRequestDTO::new),
                Arguments.of("transfer", (Supplier<FinancialTransactionRequestDTO>) TransferRequestDTO::new));
    }

    private static FinancialTransactionRequestDTO valid(Supplier<FinancialTransactionRequestDTO> factory) {
        FinancialTransactionRequestDTO dto = factory.get();
        dto.setCurrency(Currency.getInstance("USD"));
        dto.setAmount(new BigDecimal("12.50"));
        dto.setMessage("message");
        return dto;
    }

    private Set<String> violatedFields(FinancialTransactionRequestDTO dto) {
        Set<ConstraintViolation<FinancialTransactionRequestDTO>> violations = validator.validate(dto);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void amountHasNoDefault(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        assertThat(factory.get().getAmount()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void validRequestPasses(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        assertThat(violatedFields(valid(factory))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void zeroAmountIsAccepted(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        FinancialTransactionRequestDTO dto = valid(factory);
        dto.setAmount(BigDecimal.ZERO);
        assertThat(violatedFields(dto)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void missingAmountIsRejected(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        FinancialTransactionRequestDTO dto = valid(factory);
        dto.setAmount(null);
        assertThat(violatedFields(dto)).containsExactly("amount");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void negativeAmountIsRejected(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        FinancialTransactionRequestDTO dto = valid(factory);
        dto.setAmount(new BigDecimal("-0.01"));
        assertThat(violatedFields(dto)).containsExactly("amount");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestTypes")
    void missingCurrencyIsRejected(String type, Supplier<FinancialTransactionRequestDTO> factory) {
        FinancialTransactionRequestDTO dto = valid(factory);
        dto.setCurrency(null);
        assertThat(violatedFields(dto)).containsExactly("currency");
    }
}
