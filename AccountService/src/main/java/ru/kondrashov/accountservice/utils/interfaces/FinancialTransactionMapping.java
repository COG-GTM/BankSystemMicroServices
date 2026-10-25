package ru.kondrashov.accountservice.utils.interfaces;

import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.entities.FinancialTransaction;

public interface FinancialTransactionMapping {

    FinancialTransactionResponseDTO mapToFinancialTransactionResponseDTO(FinancialTransaction financialTransaction);
}
