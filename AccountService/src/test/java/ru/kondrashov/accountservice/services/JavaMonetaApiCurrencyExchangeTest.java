package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.exceptions.InvalidAmountException;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaMonetaApiCurrencyExchangeTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency BHD = Currency.getInstance("BHD");
    private static final Currency XAU = Currency.getInstance("XAU");

    private static class StubRateExchange extends JavaMonetaApiCurrencyExchange {
        private final BigDecimal rawResult;
        private boolean convertCalled;

        StubRateExchange(BigDecimal rawResult) {
            super(LogManager.getRootLogger());
            this.rawResult = rawResult;
        }

        @Override
        protected BigDecimal convert(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {
            convertCalled = true;
            return rawResult;
        }
    }

    @Test
    void sameCurrencyKeepsFullAmount() {
        StubRateExchange exchange = new StubRateExchange(BigDecimal.ONE);

        BigDecimal result = exchange.exchange(USD, USD, new BigDecimal("1234.56"));

        assertEquals(new BigDecimal("1234.56"), result);
        assertFalse(exchange.convertCalled);
    }

    @Test
    void sameCurrencyLargeAmountIsNotTruncatedToSignificantDigits() {
        StubRateExchange exchange = new StubRateExchange(BigDecimal.ONE);

        assertEquals(new BigDecimal("98765432.10"), exchange.exchange(EUR, EUR, new BigDecimal("98765432.1")));
        assertEquals(new BigDecimal("10.00"), exchange.exchange(USD, USD, BigDecimal.TEN));
    }

    @Test
    void amountFinerThanSourceMinorUnitsIsRejected() {
        StubRateExchange exchange = new StubRateExchange(BigDecimal.ONE);

        assertThrows(InvalidAmountException.class, () -> exchange.exchange(USD, USD, new BigDecimal("0.004")));
        assertThrows(InvalidAmountException.class, () -> exchange.exchange(USD, USD, new BigDecimal("10.005")));
        assertThrows(InvalidAmountException.class, () -> exchange.exchange(JPY, USD, new BigDecimal("100.5")));
        assertFalse(exchange.convertCalled);
    }

    @Test
    void trailingZerosAndSourceMinorUnitsAreAccepted() {
        StubRateExchange exchange = new StubRateExchange(BigDecimal.ONE);

        assertEquals(new BigDecimal("10.01"), exchange.exchange(USD, USD, new BigDecimal("10.0100")));
        assertEquals(new BigDecimal("0.001"), exchange.exchange(BHD, BHD, new BigDecimal("0.001")));
    }

    @Test
    void convertedAmountRoundedToTargetMinorUnits() {
        StubRateExchange exchange = new StubRateExchange(new BigDecimal("1234.5678"));

        assertEquals(new BigDecimal("1234.57"), exchange.exchange(EUR, USD, new BigDecimal("1000")));
    }

    @Test
    void convertedAmountUsesTargetCurrencyFractionDigits() {
        assertEquals(new BigDecimal("1235"), new StubRateExchange(new BigDecimal("1234.5678")).exchange(USD, JPY, BigDecimal.TEN));
        assertEquals(new BigDecimal("1234.568"), new StubRateExchange(new BigDecimal("1234.5678")).exchange(USD, BHD, BigDecimal.TEN));
    }

    @Test
    void currencyWithoutMinorUnitsIsNotRounded() {
        assertEquals(new BigDecimal("1.23456"), JavaMonetaApiCurrencyExchange.roundToMinorUnits(new BigDecimal("1.23456"), XAU));
    }
}
