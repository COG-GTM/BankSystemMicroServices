package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.javamoney.moneta.spi.MoneyUtils;
import org.springframework.stereotype.Service;
import ru.kondrashov.accountservice.exceptions.InvalidAmountException;
import ru.kondrashov.accountservice.services.interfaces.FinancialCurrencyExchange;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.convert.CurrencyConversion;
import javax.money.convert.MonetaryConversions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

@Service
@RequiredArgsConstructor
public class JavaMonetaApiCurrencyExchange implements FinancialCurrencyExchange {

    static final int FALLBACK_FRACTION_DIGITS = 2;

    private final Logger logger;

    @Override
    public BigDecimal exchange(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {

        requireMinorUnitPrecision(amount, originalCurrency);
        BigDecimal result = roundToMinorUnits(convert(originalCurrency, targetCurrency, amount), targetCurrency);
        if (result.signum() == 0 && amount.signum() != 0) {
            throw new InvalidAmountException("Amount " + amount + " " + originalCurrency.getCurrencyCode() + " is less than one minor unit of " + targetCurrency.getCurrencyCode());
        }
        logger.info("Currency conversion from " + originalCurrency.getCurrencyCode() + " to " + targetCurrency.getCurrencyCode() + " in the amount of " + amount + " " + originalCurrency.getCurrencyCode() + " is performed");
        return result;
    }

    protected BigDecimal convert(Currency originalCurrency, Currency targetCurrency, BigDecimal amount) {

        CurrencyUnit targetCurrencyUnit = Monetary.getCurrency(targetCurrency.getCurrencyCode());
        CurrencyUnit originalCurrencyUnit = Monetary.getCurrency(originalCurrency.getCurrencyCode());
        MonetaryAmount originalMonetaryAmount = Monetary.getDefaultAmountFactory().setCurrency(originalCurrencyUnit).setNumber(amount).create();

        CurrencyConversion toTargetCurrency = MonetaryConversions.getConversion(targetCurrencyUnit);

        return MoneyUtils.getBigDecimal(originalMonetaryAmount.with(toTargetCurrency).getNumber());
    }

    static BigDecimal roundToMinorUnits(BigDecimal amount, Currency currency) {
        return amount.setScale(minorUnitDigits(currency), RoundingMode.HALF_EVEN);
    }

    static void requireMinorUnitPrecision(BigDecimal amount, Currency currency) {
        if (amount.stripTrailingZeros().scale() > minorUnitDigits(currency)) {
            throw new InvalidAmountException("Amount " + amount + " has more than " + minorUnitDigits(currency) + " fraction digits allowed for " + currency.getCurrencyCode());
        }
    }

    private static int minorUnitDigits(Currency currency) {
        int fractionDigits = currency.getDefaultFractionDigits();
        return fractionDigits < 0 ? FALLBACK_FRACTION_DIGITS : fractionDigits;
    }
}
