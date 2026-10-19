package ru.kondrashov.accountservice.utils;

import org.springframework.stereotype.Component;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.accountservice.entities.Bill;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;

@Component
public class BillsMappingImpl implements BillsMapping {

    @Override
    public Bill mapToBill(BillRequestDTO billRequestDTO) {
        Bill bill = new Bill();
        bill.setCurrency(billRequestDTO.getCurrency());
        bill.setIsOverdraft(billRequestDTO.getIsOverdraft());
        return bill;
    }

    @Override
    public BillResponseDTO mapToBillResponseDTO(Bill bill) {

        return new BillResponseDTO(bill.getId(),bill.getCurrency(),bill.getAmount(),bill.getIsOverdraft(),bill.getAccount());
    }
}
