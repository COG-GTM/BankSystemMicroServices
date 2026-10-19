package ru.kondrashov.accountservice.services.interfaces;

import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;

import java.util.Collection;
import java.util.UUID;

/**
 * Operations scoped by personId/accountId verify that the bill belongs to the account and the account to the person,
 * and report a mismatch as not found.
 */
public interface BillsService {

    Collection<Bill> getBills();
    Bill getBill(UUID id);
    Bill getBill(UUID personId, UUID accountId, UUID billId);
    Collection<Bill> getBillsByAccountId(UUID personId, UUID accountID);
    void save(UUID personId, UUID accountId, Bill bill);
    void delete(UUID personId, UUID accountId, UUID id);
    void update(UUID personId, UUID accountId, UUID id, Bill bill);
    void commitAdjustment(UUID personId, UUID accountId, UUID billId, Adjustment adjustment);
    void commitPayment(UUID personId, UUID accountId, UUID billId, Payment payment);
    void commitTransfer(UUID personId, UUID accountId, UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer);
    Collection<Payment> getPaymentsByBillId(UUID billId);
    Collection<Adjustment> getAdjustmentsByBillId(UUID billId);
    Collection<Transfer> getTransfersBySourceBillId(UUID billId);
    Collection<Transfer> getTransfersByBeneficiaryBillId(UUID billId);
}
