package ru.kondrashov.accountservice.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kondrashov.accountservice.entities.Adjustment;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.entities.Payment;
import ru.kondrashov.accountservice.entities.Transfer;
import ru.kondrashov.accountservice.exceptions.BillNotFoundException;
import ru.kondrashov.accountservice.exceptions.InvalidAmountException;
import ru.kondrashov.accountservice.exceptions.NotEnoughMoneyException;
import ru.kondrashov.accountservice.repositories.BillsRepository;
import ru.kondrashov.accountservice.services.interfaces.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Currency;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BillServiceImpl implements BillsService {

    private final BillsRepository billsRepository;
    private final PaymentsService paymentsService;
    private final AdjustmentsService adjustmentsService;
    private final TransferService transferService;
    private final FinancialCurrencyExchange currencyConversion;

    @Override
    public Collection<Bill> getBills() {
        return billsRepository.findAll();
    }

    @Override
    public Bill getBill(UUID id) {
        return billsRepository.getBillById(id).orElseThrow(()->new BillNotFoundException("Bill with id = "+id+" not found."));
    }

    @Override
    public Collection<Bill> getBillsByAccountId(UUID accountID) {
        return billsRepository.getBillsByAccount_Id(accountID);
    }

    @Override
    public void save(Bill bill) {
        billsRepository.save(bill);
    }

    @Override
    public void delete(UUID id) {
        billsRepository.delete(getBill(id));
    }

    @Override
    public void update(UUID id, Bill newBill) {
        Bill oldBill = getBill(id);
        oldBill.setAmount(currencyConversion.exchange(oldBill.getCurrency(),newBill.getCurrency(),newBill.getAmount()));
        oldBill.setCurrency(newBill.getCurrency());
        oldBill.setIsOverdraft(newBill.getIsOverdraft());
        oldBill.setAccount(newBill.getAccount());
        billsRepository.save(oldBill);
    }

    @Override
    public void commitAdjustment(UUID billId, Adjustment adjustment) {

        Bill bill = getBill(billId);

       increaseBillsAmount(bill,convertToBillCurrency(adjustment.getCurrency(),bill,adjustment.getAmount()));

        adjustmentsService.save(adjustment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void commitPayment(UUID billId, Payment payment) {

        Bill bill = getBill(billId);

        decreaseBillsAmount(bill,convertToBillCurrency(payment.getCurrency(),bill,payment.getAmount()));

        paymentsService.save(payment);
    }


    @Override
    public void commitTransfer(UUID sourceBillId, UUID beneficiaryBillId, Transfer transfer){

        Bill sourceBill = getBill(sourceBillId);
        Bill beneficiaryBill = getBill(beneficiaryBillId);

        BigDecimal sourceAmount = convertToBillCurrency(transfer.getCurrency(), sourceBill, transfer.getAmount());
        BigDecimal beneficiaryAmount = convertToBillCurrency(transfer.getCurrency(), beneficiaryBill, transfer.getAmount());

        decreaseBillsAmount(sourceBill, sourceAmount);
        increaseBillsAmount(beneficiaryBill, beneficiaryAmount);

        transferService.save(transfer);
    }

    @Override
    public Collection<Payment> getPaymentsByBillId(UUID billId) {
        return paymentsService.getPaymentsByBillId(billId);
    }

    @Override
    public Collection<Adjustment> getAdjustmentsByBillId(UUID billId) {
        return adjustmentsService.getAdjustmentsByBillId(billId);
    }

    @Override
    public Collection<Transfer> getTransfersBySourceBillId(UUID billId) {
        return transferService.getTransfersBySourceBill_Id(billId);
    }

    @Override
    public Collection<Transfer> getTransfersByBeneficiaryBillId(UUID billId) {
        return transferService.getTransfersByBeneficiaryBill_Id(billId);
    }


    private BigDecimal convertToBillCurrency(Currency currency, Bill bill, BigDecimal amount){
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0 && amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new InvalidAmountException("Amount " + amount.toPlainString() + " " + currency.getCurrencyCode() + " has more than " + fractionDigits + " fraction digits.");
        }

        BigDecimal converted = currencyConversion.exchange(currency, bill.getCurrency(), amount);
        if (converted.signum() != amount.signum()) {
            throw new InvalidAmountException("Amount " + amount.toPlainString() + " " + currency.getCurrencyCode() + " is too small to be applied to bill with ID: " + bill.getId());
        }
        return converted;
    }

    private void decreaseBillsAmount(Bill bill, BigDecimal amount){

        bill.setAmount(bill.getAmount().subtract(amount));

        if(!(bill.getIsOverdraft()) && bill.getAmount().compareTo(BigDecimal.ZERO) == -1 ){
            throw new NotEnoughMoneyException("Not enough money for payment on bill with ID: " + bill.getId());
        }

        billsRepository.save(bill);
    }

    private void increaseBillsAmount(Bill bill, BigDecimal amount){
        bill.setAmount(bill.getAmount().add(amount));
        billsRepository.save(bill);
    }

}
