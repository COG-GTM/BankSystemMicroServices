package ru.kondrashov.accountservice.utils;

import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.entities.FinancialTransaction;

public final class FinancialTransactionMapping {

    private FinancialTransactionMapping() {
    }

    public static FinancialTransactionResponseDTO mapToFinancialTransactionResponseDTO(FinancialTransaction financialTransaction) {
        FinancialTransactionResponseDTO financialTransactionResponseDTO = new FinancialTransactionResponseDTO();
        financialTransactionResponseDTO.setId(financialTransaction.getId());
        financialTransactionResponseDTO.setCurrency(financialTransaction.getCurrency());
        financialTransactionResponseDTO.setAmount(financialTransaction.getAmount());
        financialTransactionResponseDTO.setTime(financialTransaction.getTime());
        financialTransactionResponseDTO.setMessage(financialTransaction.getMessage());
        return financialTransactionResponseDTO;
    }
}
