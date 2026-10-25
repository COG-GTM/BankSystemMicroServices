package ru.kondrashov.accountservice.services.interfaces;

import org.springframework.data.domain.Slice;
import ru.kondrashov.accountservice.controllers.dto.FinancialTransactionResponseDTO;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;

import java.util.Collection;
import java.util.UUID;

public interface BillsService {

    int MAX_TRANSACTIONS_PAGE_SIZE = 500;

    Collection<Bill> getBills();
    Bill getBill(UUID id);
    Collection<Bill> getBillsByAccountId(UUID accountID);
    void save(Bill bill);
    void delete(UUID id);
    void update (UUID id, Bill bill);
    void commitAdjustment(UUID billId, Adjustment adjustment);
    void commitPayment(UUID billId, Payment payment);
    void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer);
    Collection<Payment> getPaymentsByBillId(UUID billId);
    Collection<Adjustment> getAdjustmentsByBillId(UUID billId);
    Collection<Transfer> getTransfersBySourceBillId(UUID billId);
    Collection<Transfer> getTransfersByBeneficiaryBillId(UUID billId);
    Slice<FinancialTransactionResponseDTO> getFinancialTransactions(UUID billId, int page, int size);
}
