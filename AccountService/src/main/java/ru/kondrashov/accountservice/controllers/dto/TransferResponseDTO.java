package ru.kondrashov.accountservice.controllers.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import ru.kondrashov.accountservice.entities.Bill;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class TransferResponseDTO extends FinancialTransactionResponseDTO {

    private Bill sourceBill;

    private Bill beneficiaryBill;

    public TransferResponseDTO(UUID id, Currency currency, BigDecimal amount, LocalDateTime time, String message, Bill sourceBill, Bill beneficiaryBill) {
        super(id, currency, amount, time, message);
        this.sourceBill = sourceBill;
        this.beneficiaryBill = beneficiaryBill;
    }
}
