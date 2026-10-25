package ru.kondrashov.accountservice.utils.interfaces;

import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.entities.Payment;

public interface PaymentsMapping {

    Payment mapToPayment(PaymentRequestDTO paymentRequestDTO);

    FinancialTransactionResponseDTO mapToFinancialTransactionResponseDTO(Payment payment);
}
