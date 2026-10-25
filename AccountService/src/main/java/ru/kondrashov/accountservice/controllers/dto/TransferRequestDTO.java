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
public class TransferRequestDTO extends FinancialTransactionRequestDTO {

    @NotNull(message = "Source bill should not be null")
    private Bill sourceBill;

    @NotNull(message = "Beneficiary bill should not be null")
    private Bill beneficiaryBill;

    public TransferRequestDTO(Currency currency, BigDecimal amount, LocalDateTime time, String message, Bill sourceBill, Bill beneficiaryBill) {
        super(currency, amount, time, message);
        this.sourceBill = sourceBill;
        this.beneficiaryBill = beneficiaryBill;
    }
}
