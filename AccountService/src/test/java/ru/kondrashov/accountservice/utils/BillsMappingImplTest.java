package ru.kondrashov.accountservice.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.kondrashov.accountservice.controllers.dto.BillRequestDTO;
import ru.kondrashov.accountservice.controllers.dto.BillResponseDTO;
import ru.kondrashov.accountservice.entities.Account;
import ru.kondrashov.accountservice.entities.Bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillsMappingImplTest {

    BillsMappingImpl billsMapping = new BillsMappingImpl();

    @Test
    void mapToBillLeavesAccountForServiceToResolve() {
        BillRequestDTO billRequestDTO = new BillRequestDTO(Currency.getInstance("USD"), BigDecimal.TEN, true);

        Bill bill = billsMapping.mapToBill(billRequestDTO);

        assertEquals(billRequestDTO.getCurrency(), bill.getCurrency());
        assertEquals(billRequestDTO.getAmount(), bill.getAmount());
        assertTrue(bill.getIsOverdraft());
        assertNull(bill.getAccount());
    }

    @Test
    void mapToBillResponseDTOExposesOnlyAccountId() throws Exception {
        Account account = new Account("account", LocalDate.now(), UUID.randomUUID());
        account.setId(UUID.randomUUID());
        Bill bill = new Bill(Currency.getInstance("EUR"), BigDecimal.ONE, false, account);
        bill.setId(UUID.randomUUID());

        BillResponseDTO billResponseDTO = billsMapping.mapToBillResponseDTO(bill);

        assertEquals(bill.getId(), billResponseDTO.getId());
        assertEquals(account.getId(), billResponseDTO.getAccountId());

        JsonNode json = new ObjectMapper().valueToTree(billResponseDTO);
        assertEquals(account.getId().toString(), json.get("accountId").asText());
        assertFalse(json.has("account"));
        assertFalse(json.has("personId"));
    }
}
