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
    private static final Currency KWD = Currency.getInstance("KWD");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency XAU = Currency.getInstance("XAU");

    private final RecordingExchange exchange = new RecordingExchange(new BigDecimal("0.5"));

    @Test
    void sameCurrencyReturnsAmountUnchangedWithoutResolvingConversion() {
        BigDecimal amount = new BigDecimal("1234.56");

        assertThat(exchange.exchange(USD, USD, amount)).isSameAs(amount);
        assertThat(exchange.exchange(KWD, KWD, new BigDecimal("1.001"))).isEqualTo(new BigDecimal("1.001"));
        assertThat(exchange.createdConversions).isEmpty();
    }

    @Test
    void conversionKeepsDecimalPlacesInsteadOfSignificantDigits() {
        assertThat(exchange.exchange(USD, EUR, new BigDecimal("2469.12"))).isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    void conversionRoundsHalfEvenToTargetCurrencyMinorUnits() {
        assertThat(exchange.exchange(USD, EUR, new BigDecimal("2469.13"))).isEqualTo(new BigDecimal("1234.56"));
        assertThat(exchange.exchange(USD, EUR, new BigDecimal("2469.15"))).isEqualTo(new BigDecimal("1234.58"));
        assertThat(exchange.exchange(USD, KWD, new BigDecimal("2.002"))).isEqualTo(new BigDecimal("1.001"));
        assertThat(exchange.exchange(USD, JPY, new BigDecimal("2469.13"))).isEqualTo(new BigDecimal("1235"));
        assertThat(exchange.exchange(USD, XAU, new BigDecimal("3.333"))).isEqualTo(new BigDecimal("1.67"));
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
