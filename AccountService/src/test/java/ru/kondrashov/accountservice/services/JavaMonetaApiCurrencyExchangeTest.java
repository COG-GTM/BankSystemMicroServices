package ru.kondrashov.accountservice.services;

import org.apache.logging.log4j.LogManager;
import org.javamoney.moneta.spi.MoneyUtils;
import org.junit.jupiter.api.Test;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavaMonetaApiCurrencyExchangeTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency RUB = Currency.getInstance("RUB");

    private final RecordingExchange exchange = new RecordingExchange(new BigDecimal("0.5"));

    @Test
    void sameCurrencyReturnsAmountWithoutResolvingConversion() {
        BigDecimal result = exchange.exchange(USD, USD, new BigDecimal("1234.56"));

        assertThat(result).isEqualByComparingTo("1234.56");
        assertThat(result.scale()).isEqualTo(2);
        assertThat(exchange.createdConversions).isEmpty();
    }

    @Test
    void sameCurrencyRoundsHalfEvenToTwoDecimalPlaces() {
        assertThat(exchange.exchange(USD, USD, new BigDecimal("10.125"))).isEqualTo(new BigDecimal("10.12"));
        assertThat(exchange.exchange(USD, USD, new BigDecimal("10.135"))).isEqualTo(new BigDecimal("10.14"));
        assertThat(exchange.exchange(USD, USD, BigDecimal.TEN)).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void conversionKeepsDecimalPlacesInsteadOfSignificantDigits() {
        BigDecimal result = exchange.exchange(USD, EUR, new BigDecimal("2469.13"));

        assertThat(result).isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    void conversionIsResolvedOncePerTargetCurrency() {
        exchange.exchange(USD, EUR, BigDecimal.ONE);
        exchange.exchange(RUB, EUR, BigDecimal.TEN);
        exchange.exchange(USD, EUR, BigDecimal.ONE);
        exchange.exchange(EUR, RUB, BigDecimal.ONE);
        exchange.exchange(USD, RUB, BigDecimal.ONE);

        assertThat(exchange.createdConversions).containsExactly("EUR", "RUB");
    }

    private static final class RecordingExchange extends JavaMonetaApiCurrencyExchange {

        private final BigDecimal factor;
        private final List<String> createdConversions = new ArrayList<>();

        RecordingExchange(BigDecimal factor) {
            super(LogManager.getLogger(JavaMonetaApiCurrencyExchangeTest.class));
            this.factor = factor;
        }

        @Override
        protected CurrencyConversion createConversion(String targetCurrencyCode) {
            createdConversions.add(targetCurrencyCode);
            CurrencyConversion conversion = mock(CurrencyConversion.class);
            when(conversion.apply(any(MonetaryAmount.class))).thenAnswer(invocation -> {
                MonetaryAmount amount = invocation.getArgument(0);
                return Monetary.getDefaultAmountFactory()
                        .setCurrency(targetCurrencyCode)
                        .setNumber(MoneyUtils.getBigDecimal(amount.getNumber()).multiply(factor))
                        .create();
            });
            return conversion;
        }
    }
}
