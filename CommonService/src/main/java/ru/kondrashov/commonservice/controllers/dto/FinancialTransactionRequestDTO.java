package ru.kondrashov.commonservice.controllers.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

@Data
public class FinancialTransactionRequestDTO {

    private UUID id;

    @NotNull(message = "Currency should not be null")
    private Currency currency;

    @Min(message = "Amount should not be negative", value = 0)
    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    private LocalDateTime time = LocalDateTime.now();

    @NotEmpty(message = "Message should not be empty")
    private String message;

}
