package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.javamoney.moneta.spi.MoneyUtils;
import org.springframework.stereotype.Service;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;

import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.MonetaryConversions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class JavaMonetaApiCurrencyExchange implements FinancialCurrencyExchange {

    // Matches the scale of the persisted bill and transaction amount columns (Hibernate default numeric(19,2)).
    static final int PERSISTED_AMOUNT_SCALE = 2;

    private final Logger logger;

    // Moneta conversions look up the provider's current rate on every apply, so caching them does not freeze rates.
    private final ConcurrentMap<String, CurrencyConversion> conversionsByTargetCurrency = new ConcurrentHashMap<>();

    @Override
    public BigDecimal exchange(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {

        if (originalCurrency.equals(targetCurrency)) {
            // The request amount is also persisted as the transaction, so only round to the shared column scale.
            return amount.setScale(PERSISTED_AMOUNT_SCALE, RoundingMode.HALF_EVEN);
        }

        MonetaryAmount originalMonetaryAmount = Monetary.getDefaultAmountFactory()
                .setCurrency(originalCurrency.getCurrencyCode())
                .setNumber(amount)
                .create();

        BigDecimal result = roundForStorage(
                MoneyUtils.getBigDecimal(originalMonetaryAmount.with(conversionTo(targetCurrency)).getNumber()), targetCurrency);
        logger.info("Currency conversion from {} to {} in the amount of {} {} is performed",
                originalCurrency.getCurrencyCode(), targetCurrency.getCurrencyCode(), amount, originalCurrency.getCurrencyCode());
        return result;
    }

    private static BigDecimal roundForStorage(BigDecimal amount, Currency currency) {
        int fractionDigits = currency.getDefaultFractionDigits();
        int scale = fractionDigits < 0 ? PERSISTED_AMOUNT_SCALE : Math.min(fractionDigits, PERSISTED_AMOUNT_SCALE);
        return amount.setScale(scale, RoundingMode.HALF_EVEN).setScale(PERSISTED_AMOUNT_SCALE);
    }

    private CurrencyConversion conversionTo(Currency targetCurrency) {
        return conversionsByTargetCurrency.computeIfAbsent(targetCurrency.getCurrencyCode(), this::createConversion);
    }

    protected CurrencyConversion createConversion(String targetCurrencyCode) {
        return MonetaryConversions.getConversion(targetCurrencyCode);
    }
}
