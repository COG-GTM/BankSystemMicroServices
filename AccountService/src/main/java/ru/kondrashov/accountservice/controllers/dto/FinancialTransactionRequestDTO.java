package ru.kondrashov.accountservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;

/**
 * Shared request body for payments, adjustments and transfers.
 * Bills are identified by the request path, never by the body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class FinancialTransactionRequestDTO {

    @NotNull(message = "Currency should not be null")
    private Currency currency;

    @NotNull(message = "Amount should not be null")
    @DecimalMin(message = "Amount should not be negative", value = "0")
    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    private LocalDateTime time = LocalDateTime.now();

    @NotEmpty(message = "Message should not empty")
    private String message;
}
