package ru.kondrashov.commonservice.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommonServiceImpl implements CommonService{

    private static final ParameterizedTypeReference<Void> NO_BODY = new ParameterizedTypeReference<Void>() {};

    private final RestTemplate restTemplate;
    private final Logger logger;

    @Value("${people.URL}")
    private String peopleUrl;
    @Value("${person.URL}")
    private String personUrl;
    @Value("${accounts.URL}")
    private String accountsUrl;
    @Value("${account.URL}")
    private String accountUrl;
    @Value("${bills.URL}")
    private String billsUrl;
    @Value("${bill.URL}")
    private String billUrl;
    @Value("${transactions.URL}")
    private String transactionsUrl;

    @Override
    public PersonRequestDTO getPersonById(UUID uuid){
        return get(personUrl, new ParameterizedTypeReference<PersonRequestDTO>() {}, uuid);
    }

    @Override
    public Collection<PersonRequestDTO> getPeople() {
        return get(peopleUrl, new ParameterizedTypeReference<List<PersonRequestDTO>>() {});
    }

    @Override
    public Collection<AccountRequestDTO> getAccountsByPerson(UUID id) {
        return get(accountsUrl, new ParameterizedTypeReference<List<AccountRequestDTO>>() {}, id);
    }

    @Override
    public Collection<BillRequestDTO> getBillsByAccountId(UUID personId, UUID accountId) {
        return get(billsUrl, new ParameterizedTypeReference<List<BillRequestDTO>>() {}, personId, accountId);
    }

    @Override
    public Collection<FinancialTransactionRequestDTO> getFinancialTransactionsByBillId(UUID personId, UUID accountId, UUID billId) {
        return get(transactionsUrl, new ParameterizedTypeReference<List<FinancialTransactionRequestDTO>>() {},
                personId, accountId, billId);
    }


    @Override
    public void createPerson(PersonResponseDTO person) throws Exception {
        post(peopleUrl, person);
    }

    @Override
    public void createAccount(UUID personId, AccountResponseDTO account) throws Exception {
        post(accountsUrl, account, personId);
    }

    @Override
    public void createBill(UUID personId, UUID accountId, BillResponseDTO bill) throws Exception {
        post(billsUrl, bill, personId, accountId);
    }


    @Override
    public void updatePerson(UUID id, PersonResponseDTO personResponseDTO){
        put(personUrl, personResponseDTO, id);
    }

    @Override
    public void updateAccount(UUID personId, UUID accountId, AccountResponseDTO accountResponseDTO) {
        put(accountUrl, accountResponseDTO, personId, accountId);
    }

    @Override
    public void updateBill(UUID personId, UUID accountId, UUID billId, BillResponseDTO billResponseDTO) {
        put(billUrl, billResponseDTO, personId, accountId, billId);
    }


    @Override
    public void deletePerson(UUID id) {
        for (AccountRequestDTO account:getAccountsByPerson(id)) {
            deleteAccount(id,account.getId());
        }
        delete(personUrl, id);
    }

    @Override
    public void deleteAccount(UUID personId, UUID accountId) {
        delete(accountUrl, personId, accountId);
    }

    @Override
    public void deleteBill(UUID personId, UUID accountId, UUID billId) {
        delete(billUrl, personId, accountId, billId);
    }


    private <T> T get(String urlTemplate, ParameterizedTypeReference<T> responseType, Object... uriVars) {
        return exchange(HttpMethod.GET, urlTemplate, null, responseType, uriVars).getBody();
    }

    private void post(String urlTemplate, Object body, Object... uriVars) throws Exception {
        ResponseEntity<Void> responseEntity = exchange(HttpMethod.POST, urlTemplate, body, NO_BODY, uriVars);
        if (responseEntity.getStatusCode() != HttpStatus.CREATED) {
            logger.error("Expected {} but got {}", HttpStatus.CREATED, responseEntity);
            throw new Exception(responseEntity.toString());
        }
    }

    private void put(String urlTemplate, Object body, Object... uriVars) {
        exchange(HttpMethod.PUT, urlTemplate, body, NO_BODY, uriVars);
    }

    private void delete(String urlTemplate, Object... uriVars) {
        exchange(HttpMethod.DELETE, urlTemplate, null, NO_BODY, uriVars);
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String urlTemplate, Object body,
                                           ParameterizedTypeReference<T> responseType, Object... uriVars) {
        String url = String.format(urlTemplate, uriVars);
        logger.info("Send {} request to {}", method, url);
        HttpEntity<?> request = body == null ? null : new HttpEntity<>(body);
        ResponseEntity<T> responseEntity = restTemplate.exchange(url, method, request, responseType);
        logger.info("Got response with status code {}", responseEntity.getStatusCode());
        return responseEntity;
    }
}
