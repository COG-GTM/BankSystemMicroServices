package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import ru.kondrashov.accountservice.exceptions.InvalidAmountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaMonetaApiCurrencyExchangeTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final Currency XAU = Currency.getInstance("XAU");

    private final JavaMonetaApiCurrencyExchange exchange = new JavaMonetaApiCurrencyExchange(LogManager.getLogger());

    @Test
    void sameCurrencyKeepsAmountsWithMoreThanTwoSignificantDigits() {
        assertThat(exchange.exchange(USD, USD, new BigDecimal("1234.56"))).isEqualTo(new BigDecimal("1234.56"));
        assertThat(exchange.exchange(USD, USD, new BigDecimal("98765432.10"))).isEqualTo(new BigDecimal("98765432.10"));
    }

    @Test
    void sameCurrencyKeepsSmallAmounts() {
        assertThat(exchange.exchange(USD, USD, new BigDecimal("5.75"))).isEqualTo(new BigDecimal("5.75"));
        assertThat(exchange.exchange(USD, USD, BigDecimal.TEN)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void roundsToTargetCurrencyMinorUnitsHalfEven() {
        JavaMonetaApiCurrencyExchange fixedRate = withConvertedValue(new BigDecimal("1234.5650"));

        assertThat(fixedRate.exchange(USD, USD, BigDecimal.ONE)).isEqualTo(new BigDecimal("1234.56"));
        assertThat(fixedRate.exchange(USD, JPY, BigDecimal.ONE)).isEqualTo(new BigDecimal("1235"));
        assertThat(fixedRate.exchange(USD, BHD, BigDecimal.ONE)).isEqualTo(new BigDecimal("1234.565"));
    }

    @Test
    void currencyWithoutMinorUnitsFallsBackToTwoDecimals() {
        JavaMonetaApiCurrencyExchange fixedRate = withConvertedValue(new BigDecimal("1234.5678"));

        assertThat(fixedRate.exchange(USD, XAU, BigDecimal.ONE)).isEqualTo(new BigDecimal("1234.57"));
    }

    @Test
    void validatedAmountFinerThanSourceMinorUnitIsRejected() {
        assertThatThrownBy(() -> exchange.exchangeValidated(USD, USD, new BigDecimal("0.004")))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> exchange.exchangeValidated(JPY, JPY, new BigDecimal("100.5")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void validatedAmountWithTrailingZerosIsAccepted() {
        assertThat(exchange.exchangeValidated(USD, USD, new BigDecimal("10.0100"))).isEqualTo(new BigDecimal("10.01"));
    }

    @Test
    void validatedAmountThatConvertsToZeroIsRejected() {
        JavaMonetaApiCurrencyExchange tinyRate = withConvertedValue(new BigDecimal("0.0032"));

        assertThatThrownBy(() -> tinyRate.exchangeValidated(BHD, USD, new BigDecimal("0.001")))
                .isInstanceOf(InvalidAmountException.class);
    }

    @Test
    void validatedExchangeChecksPrecisionAgainstSourceCurrency() {
        JavaMonetaApiCurrencyExchange fixedRate = withConvertedValue(new BigDecimal("3.2741"));

        assertThatThrownBy(() -> fixedRate.exchangeValidated(USD, BHD, new BigDecimal("1.234")))
                .isInstanceOf(InvalidAmountException.class);
        assertThat(fixedRate.exchangeValidated(BHD, USD, new BigDecimal("1.234"))).isEqualTo(new BigDecimal("3.27"));
    }

    @Test
    void plainExchangeAcceptsStoredBalancesFinerThanMinorUnit() {
        assertThat(exchange.exchange(JPY, JPY, new BigDecimal("100.50"))).isEqualTo(new BigDecimal("100"));
    }

    private static JavaMonetaApiCurrencyExchange withConvertedValue(BigDecimal converted) {
        return new JavaMonetaApiCurrencyExchange(LogManager.getLogger()) {
            @Override
            protected BigDecimal convert(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {
                return converted;
            }
        };
    }
}
