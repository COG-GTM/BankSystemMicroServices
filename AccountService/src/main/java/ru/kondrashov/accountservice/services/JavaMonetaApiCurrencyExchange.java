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

    static final int AMOUNT_SCALE = 2;

    private final Logger logger;

    // Moneta conversions look up the provider's current rate on every apply, so caching them does not freeze rates.
    private final ConcurrentMap<String, CurrencyConversion> conversionsByTargetCurrency = new ConcurrentHashMap<>();

    @Override
    public BigDecimal exchange(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {

        if (originalCurrency.equals(targetCurrency)) {
            return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
        }

        MonetaryAmount originalMonetaryAmount = Monetary.getDefaultAmountFactory()
                .setCurrency(originalCurrency.getCurrencyCode())
                .setNumber(amount)
                .create();

        BigDecimal result = MoneyUtils.getBigDecimal(originalMonetaryAmount.with(conversionTo(targetCurrency)).getNumber())
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_EVEN);
        logger.info("Currency conversion from {} to {} in the amount of {} {} is performed",
                originalCurrency.getCurrencyCode(), targetCurrency.getCurrencyCode(), amount, originalCurrency.getCurrencyCode());
        return result;
    }

    private CurrencyConversion conversionTo(Currency targetCurrency) {
        return conversionsByTargetCurrency.computeIfAbsent(targetCurrency.getCurrencyCode(), this::createConversion);
    }

    protected CurrencyConversion createConversion(String targetCurrencyCode) {
        return MonetaryConversions.getConversion(targetCurrencyCode);
    }
}
