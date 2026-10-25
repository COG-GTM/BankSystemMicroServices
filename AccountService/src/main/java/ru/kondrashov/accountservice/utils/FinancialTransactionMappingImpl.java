package ru.kondrashov.accountservice.utils;

import org.springframework.stereotype.Component;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.entities.FinancialTransaction;
import ru.kondrashov.accountservice.utils.interfaces.FinancialTransactionMapping;

@Component
public class FinancialTransactionMappingImpl implements FinancialTransactionMapping {

    @Override
    public FinancialTransactionResponseDTO mapToFinancialTransactionResponseDTO(FinancialTransaction financialTransaction) {
        FinancialTransactionResponseDTO financialTransactionResponseDTO = new FinancialTransactionResponseDTO();
        financialTransactionResponseDTO.setId(financialTransaction.getId());
        financialTransactionResponseDTO.setCurrency(financialTransaction.getCurrency());
        financialTransactionResponseDTO.setAmount(financialTransaction.getAmount());
        financialTransactionResponseDTO.setTime(financialTransaction.getTime());
        financialTransactionResponseDTO.setMessage(financialTransaction.getMessage());
        return financialTransactionResponseDTO;
    }
}
