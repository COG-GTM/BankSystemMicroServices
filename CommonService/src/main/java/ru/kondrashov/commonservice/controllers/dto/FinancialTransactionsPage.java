package ru.kondrashov.commonservice.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class FinancialTransactionsPage {

    private final List<FinancialTransactionRequestDTO> transactions;
    private final int page;
    private final boolean hasNextPage;
}
