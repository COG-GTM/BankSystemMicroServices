package ru.kondrashov.accountservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import ru.kondrashov.accountservice.audit.AuditContextResolver;
import ru.kondrashov.accountservice.entities.Bill;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Currency;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdjustmentRequestDTO {

    @NotNull(message = "Message should not null")
    private Currency currency;

    @Min(message = "Amount should be more than zero", value = 0)
    private BigDecimal amount;

    @DateTimeFormat(pattern = "yyyy-MM-dd hh:mm:ss")
    private LocalDateTime time = LocalDateTime.now();

    @NotEmpty(message = "Message should not empty")
    private String message;

    @NotNull(message = "Bill should not be null")
    private Bill bill;

    @NotNull(message = "Approver should not be null")
    @Pattern(regexp = AuditContextResolver.IDENTIFIER_REGEXP, message = "Approver should be a user id of 1-128 characters [A-Za-z0-9._@:-]")
    private String approvedBy;
}
