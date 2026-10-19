package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class JavaMonetaApiCurrencyExchangeTest {

    private final JavaMonetaApiCurrencyExchange currencyExchange = new JavaMonetaApiCurrencyExchange(mock(Logger.class));

    @Test
    void sameCurrencyKeepsFullAmount() {
        Currency usd = Currency.getInstance("USD");

        assertEquals(new BigDecimal("1234.56"), currencyExchange.exchange(usd, usd, new BigDecimal("1234.56")));
    }

    @Test
    void sameCurrencyRoundsToMinorUnitsHalfEven() {
        Currency usd = Currency.getInstance("USD");

        assertEquals(new BigDecimal("10.00"), currencyExchange.exchange(usd, usd, new BigDecimal("10.005")));
        assertEquals(new BigDecimal("10.02"), currencyExchange.exchange(usd, usd, new BigDecimal("10.015")));
        assertEquals(new BigDecimal("100.00"), currencyExchange.exchange(usd, usd, BigDecimal.valueOf(100)));
    }

    @Test
    void sameCurrencyWithoutMinorUnitsRoundsToWholeUnits() {
        Currency jpy = Currency.getInstance("JPY");

        assertEquals(new BigDecimal("1235"), currencyExchange.exchange(jpy, jpy, new BigDecimal("1234.56")));
    }
}
