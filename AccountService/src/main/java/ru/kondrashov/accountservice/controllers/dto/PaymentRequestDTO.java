package ru.kondrashov.accountservice.controllers.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.kondrashov.accountservice.entities.Bill;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class PaymentRequestDTO extends FinancialTransactionRequestDTO {

    @NotNull(message = "Bill should not be null")
    private Bill bill;

    public PaymentRequestDTO(Currency currency, BigDecimal amount, LocalDateTime time, String message, Bill bill) {
        super(currency, amount, time, message);
        this.bill = bill;
    }
}
