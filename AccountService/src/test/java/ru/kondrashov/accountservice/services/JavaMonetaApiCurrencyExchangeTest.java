package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class JavaMonetaApiCurrencyExchangeTest {

    private final JavaMonetaApiCurrencyExchange currencyExchange = new JavaMonetaApiCurrencyExchange(LogManager.getRootLogger());

    private final Currency usd = Currency.getInstance("USD");

    @Test
    void sameCurrencyExchangeKeepsAmountWithManySignificantDigits() {
        assertThat(currencyExchange.exchange(usd, usd, new BigDecimal("1234.56"))).isEqualByComparingTo("1234.56");
    }

    @Test
    void sameCurrencyExchangeKeepsCentsOfSmallAmount() {
        assertThat(currencyExchange.exchange(usd, usd, new BigDecimal("5.75"))).isEqualByComparingTo("5.75");
    }

    @Test
    void exchangeRoundsToMinorUnitsOfTargetCurrencyUsingHalfEven() {
        assertThat(currencyExchange.exchange(usd, usd, new BigDecimal("10.125"))).isEqualTo(new BigDecimal("10.12"));
        assertThat(currencyExchange.exchange(usd, usd, new BigDecimal("10.135"))).isEqualTo(new BigDecimal("10.14"));
    }

    @Test
    void exchangeUsesTargetCurrencyFractionDigits() {
        Currency jpy = Currency.getInstance("JPY");
        assertThat(currencyExchange.exchange(jpy, jpy, new BigDecimal("1234.5"))).isEqualTo(new BigDecimal("1234"));
    }
}
