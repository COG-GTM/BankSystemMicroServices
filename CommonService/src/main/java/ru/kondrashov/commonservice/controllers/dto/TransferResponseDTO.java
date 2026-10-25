package ru.kondrashov.commonservice.controllers.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

@Data
public class TransferResponseDTO {

    @NotNull(message = "Currency should not be null")
    private Currency currency;

    @NotNull(message = "Amount should not be null")
    @DecimalMin(message = "Amount should be greater than zero", value = "0", inclusive = false)
    private BigDecimal amount;

    @NotEmpty(message = "Message should not be empty")
    private String message;

    @JsonIgnore
    @NotNull(message = "Beneficiary bill should not be null")
    private UUID beneficiaryBillId;
}
