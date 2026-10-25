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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CommonServiceImpl implements CommonService{

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
    public PersonResponseDTO getPersonById(UUID uuid){
        return restTemplate.getForObject(String.format(personUrl,uuid), PersonResponseDTO.class);

    }

    @Override
    public Collection<PersonResponseDTO> getPeople() {
        logger.info("Send GET request to "+peopleUrl);
        ResponseEntity<List<PersonResponseDTO>> responseEntity =
                restTemplate.exchange(
                        peopleUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<PersonResponseDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<AccountResponseDTO> getAccountsByPerson(UUID id) {
        logger.info("Send GET request to "+String.format(accountsUrl,id));
        ResponseEntity<List<AccountResponseDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(accountsUrl,id),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<AccountResponseDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<BillResponseDTO> getBillsByAccountId(UUID personId, UUID accountId) {
        logger.info("Send GET request to "+String.format(billsUrl,personId,accountId));
        ResponseEntity<List<BillResponseDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(billsUrl,personId,accountId),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<BillResponseDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<FinancialTransactionResponseDTO> getFinancialTransactionsByBillId(UUID personId, UUID accountId, UUID billId) {
        logger.info("Send GET request to "+String.format(transactionsUrl,personId, accountId, billId));
        ResponseEntity<List<FinancialTransactionResponseDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(transactionsUrl,personId, accountId, billId),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<FinancialTransactionResponseDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }


    @Override
    public void createPerson(PersonRequestDTO person) throws Exception {
        logger.info("Send POST request to "+peopleUrl);
        ResponseEntity<Void> responseEntity =
                restTemplate.postForEntity(peopleUrl, person, Void.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }

    @Override
    public void createAccount(UUID personId, AccountRequestDTO account) throws Exception {
        logger.info("Send POST request to "+String.format(accountsUrl, personId));
        ResponseEntity<Void> responseEntity =
                restTemplate.postForEntity(String.format(accountsUrl, personId), account, Void.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }

    @Override
    public void createBill(UUID personId, UUID accountId, BillRequestDTO bill) throws Exception {
        logger.info("Send POST request to "+String.format(billsUrl, personId, accountId));
        ResponseEntity<Void> responseEntity =
                restTemplate.postForEntity(String.format(billsUrl, personId, accountId), bill, Void.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }


    @Override
    public void updatePerson(UUID id, PersonRequestDTO personRequestDTO){
        logger.info("Send PUT request to "+String.format(personUrl,id));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(id.toString()));
        HttpEntity<PersonRequestDTO> personRequestDTOHttpEntity = new HttpEntity<>(personRequestDTO, params);
        ResponseEntity<Void> responseEntity = restTemplate.exchange(String.format(personUrl,id),HttpMethod.PUT, personRequestDTOHttpEntity, Void.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }

    @Override
    public void updateAccount(UUID personId, UUID accountId, AccountRequestDTO accountRequestDTO) {
        logger.info("Send PUT request to "+String.format(accountUrl, personId, accountId));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        HttpEntity<AccountRequestDTO> accountRequestDTOHttpEntity = new HttpEntity<>(accountRequestDTO, params);
        ResponseEntity<Void> responseEntity = restTemplate.exchange(String.format(accountUrl, personId, accountId),HttpMethod.PUT, accountRequestDTOHttpEntity, Void.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }


    @Override
    public void updateBill(UUID personId, UUID accountId, UUID billId, BillRequestDTO billRequestDTO) {
        logger.info("Send PUT request to "+String.format(billUrl, personId, accountId, billId));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(billId.toString()));
        HttpEntity<BillRequestDTO> billRequestDTOHttpEntity = new HttpEntity<>(billRequestDTO, params);
        ResponseEntity<Void> responseEntity = restTemplate.exchange(String.format(billUrl, personId, accountId, billId), HttpMethod.PUT, billRequestDTOHttpEntity, Void.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }


    @Override
    public void deletePerson(UUID id) {
        logger.info("Send DELETE request to "+String.format(personUrl,id));
        for (AccountResponseDTO account:getAccountsByPerson(id)) {
            deleteAccount(id,account.getId());
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(id.toString()));
        ResponseEntity<Object> responseEntity = restTemplate.exchange(String.format(personUrl,id), HttpMethod.DELETE, null, Object.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }

    @Override
    public void deleteAccount(UUID personId, UUID accountId) {
        logger.info("Send DELETE request to "+String.format(accountUrl, personId, accountId));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        ResponseEntity<Object> responseEntity = restTemplate.exchange(String.format(accountUrl, personId, accountId), HttpMethod.DELETE, null, Object.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }

    @Override
    public void deleteBill(UUID personId, UUID accountId, UUID billId) {
        logger.info("Send DELETE request to "+String.format(billUrl, personId, accountId, billId));

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        ResponseEntity<Object> responseEntity = restTemplate.exchange(String.format(billUrl, personId, accountId, billId), HttpMethod.DELETE, null, Object.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }
}
