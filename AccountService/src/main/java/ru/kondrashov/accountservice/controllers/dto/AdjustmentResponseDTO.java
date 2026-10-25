package ru.kondrashov.accountservice.controllers.dto;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class AdjustmentResponseDTO extends FinancialTransactionResponseDTO {

    public AdjustmentResponseDTO(UUID id, Currency currency, BigDecimal amount, LocalDateTime time, String message) {
        super(id, currency, amount, time, message);
    }
}
