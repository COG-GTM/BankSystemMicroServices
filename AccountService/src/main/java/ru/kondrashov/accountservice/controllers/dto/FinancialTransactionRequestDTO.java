package ru.kondrashov.accountservice.controllers.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

@Data
@NoArgsConstructor
public abstract class FinancialTransactionRequestDTO {

    @NotNull(message = "Currency should not be null")
    private Currency currency;

    @NotNull(message = "Amount should not be null")
    @DecimalMin(value = "0", inclusive = false, message = "Amount should be more than zero")
    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time = LocalDateTime.now();

    @NotEmpty(message = "Message should not be empty")
    private String message;

    protected FinancialTransactionRequestDTO(Currency currency, BigDecimal amount, LocalDateTime time, String message) {
        this.currency = currency;
        this.amount = amount;
        this.time = time;
        this.message = message;
    }
}
