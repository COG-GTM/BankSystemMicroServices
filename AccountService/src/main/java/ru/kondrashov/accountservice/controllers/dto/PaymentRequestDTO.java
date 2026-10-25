package ru.kondrashov.accountservice.controllers.dto;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PaymentRequestDTO extends FinancialTransactionRequestDTO {

    public PaymentRequestDTO(Currency currency, BigDecimal amount, LocalDateTime time, String message) {
        super(currency, amount, time, message);
    }
}
