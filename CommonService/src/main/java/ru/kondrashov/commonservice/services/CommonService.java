package ru.kondrashov.commonservice.services;

import ru.kondrashov.commonservice.controllers.dto.*;

import java.util.Collection;
import java.util.UUID;

public interface CommonService {

    PersonResponseDTO getPersonById(UUID uuid);

    Collection<PersonResponseDTO> getPeople();

    Collection<AccountResponseDTO> getAccountsByPerson(UUID id);

    Collection<BillResponseDTO> getBillsByAccountId(UUID personId, UUID accountId);



    void createPerson(PersonRequestDTO person) throws Exception;

    void createAccount(UUID personId, AccountRequestDTO account) throws Exception;

    void createBill(UUID personId, UUID accountId, BillRequestDTO bill) throws Exception;


    void updatePerson(UUID id, PersonRequestDTO personRequestDTO);

    void updateAccount(UUID personId, UUID accountId, AccountRequestDTO accountRequestDTO);

    void updateBill(UUID personId, UUID accountId, UUID billId, BillRequestDTO billRequestDTO);



    void deleteAccount(UUID personId, UUID accountId);

    void deletePerson(UUID id);

    void deleteBill(UUID personId, UUID accountId, UUID billId);

    Collection<FinancialTransactionResponseDTO> getFinancialTransactionsByBillId(UUID personId, UUID accountId, UUID billId);
}
