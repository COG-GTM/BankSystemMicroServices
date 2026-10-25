package ru.kondrashov.commonservice.controllers.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

@Data
public class FinancialTransactionRequestDTO {

    private UUID id;

    private Currency currency;

    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    private LocalDateTime time;

    private String message;

}
