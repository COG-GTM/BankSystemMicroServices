package ru.kondrashov.accountservice.services.interfaces;

import java.math.BigDecimal;
import java.util.Currency;

public interface FinancialCurrencyExchange {


    BigDecimal exchange(Currency originalCurrency, Currency targetCurrency, BigDecimal amount);

    BigDecimal exchangeTransactionAmount(Currency transactionCurrency, Currency billCurrency, BigDecimal amount);
}
