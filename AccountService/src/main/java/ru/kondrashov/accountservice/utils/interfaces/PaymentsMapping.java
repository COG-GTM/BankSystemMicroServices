package ru.kondrashov.accountservice.utils.interfaces;

import ru.kondrashov.accountservice.controllers.dto.PaymentRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.PaymentResponseDTO;
import ru.kondrashov.accountservice.entities.Payment;

public interface PaymentsMapping {

    Payment mapToPayment(PaymentRequestDTO paymentRequestDTO);

    PaymentResponseDTO mapToPaymentResponseDTO(Payment payment);
}
