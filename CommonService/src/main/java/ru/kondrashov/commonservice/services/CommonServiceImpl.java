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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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
    public PersonRequestDTO getPersonById(UUID uuid){
        return restTemplate.getForObject(String.format(personUrl,uuid), PersonRequestDTO.class);

    }

    @Override
    public Collection<PersonRequestDTO> getPeople() {
        logger.info("Send GET request to "+peopleUrl);
        ResponseEntity<List<PersonRequestDTO>> responseEntity =
                restTemplate.exchange(
                        peopleUrl,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<PersonRequestDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<AccountRequestDTO> getAccountsByPerson(UUID id) {
        logger.info("Send GET request to "+String.format(accountsUrl,id));
        ResponseEntity<List<AccountRequestDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(accountsUrl,id),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<AccountRequestDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<BillRequestDTO> getBillsByAccountId(UUID personId, UUID accountId) {
        logger.info("Send GET request to "+String.format(billsUrl,personId,accountId));
        ResponseEntity<List<BillRequestDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(billsUrl,personId,accountId),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<BillRequestDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }

    @Override
    public Collection<FinancialTransactionRequestDTO> getFinancialTransactionsByBillId(UUID personId, UUID accountId, UUID billId) {
        logger.info("Send GET request to "+String.format(transactionsUrl,personId, accountId, billId));
        ResponseEntity<List<FinancialTransactionRequestDTO>> responseEntity =
                restTemplate.exchange(
                        String.format(transactionsUrl,personId, accountId, billId),
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<FinancialTransactionRequestDTO>>() {}
                );
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity.getBody();
    }


    @Override
    public void createPerson(PersonResponseDTO person) {
        post(peopleUrl, person, PersonResponseDTO.class);
    }

    @Override
    public void createAccount(UUID personId, AccountResponseDTO account) {
        post(String.format(accountsUrl, personId), account, AccountResponseDTO.class);
    }

    @Override
    public void createBill(UUID personId, UUID accountId, BillResponseDTO bill) {
        post(String.format(billsUrl, personId, accountId), bill, BillResponseDTO.class);
    }


    @Override
    public void updatePerson(UUID id, PersonResponseDTO personResponseDTO){
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(id.toString()));
        send(HttpMethod.PUT, String.format(personUrl,id), new HttpEntity<>(personResponseDTO, params), PersonResponseDTO.class);
    }

    @Override
    public void updateAccount(UUID personId, UUID accountId, AccountResponseDTO accountResponseDTO) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        send(HttpMethod.PUT, String.format(accountUrl, personId, accountId), new HttpEntity<>(accountResponseDTO, params), AccountResponseDTO.class);
    }


    @Override
    public void updateBill(UUID personId, UUID accountId, UUID billId, BillResponseDTO billResponseDTO) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(billId.toString()));
        send(HttpMethod.PUT, String.format(billUrl, personId, accountId, billId), new HttpEntity<>(billResponseDTO, params), BillResponseDTO.class);
    }


    @Override
    public void deletePerson(UUID id) {
        for (AccountRequestDTO account:getAccountsByPerson(id)) {
            deleteAccount(id,account.getId());
        }
        send(HttpMethod.DELETE, String.format(personUrl,id), null, Object.class);
    }

    @Override
    public void deleteAccount(UUID personId, UUID accountId) {
        send(HttpMethod.DELETE, String.format(accountUrl, personId, accountId), null, Object.class);
    }

    @Override
    public void deleteBill(UUID personId, UUID accountId, UUID billId) {
        send(HttpMethod.DELETE, String.format(billUrl, personId, accountId, billId), null, Object.class);
    }

    private <T> void post(String url, Object body, Class<T> responseType) {
        ResponseEntity<T> responseEntity = send(HttpMethod.POST, url, new HttpEntity<>(body), responseType);
        if (responseEntity.getStatusCode() != HttpStatus.CREATED) {
            throw new DownstreamServiceException(HttpMethod.POST, url, responseEntity.getStatusCodeValue(), null, null);
        }
    }

    private <T> ResponseEntity<T> send(HttpMethod method, String url, HttpEntity<?> entity, Class<T> responseType) {
        logger.info("Send " + method + " request to " + url);
        ResponseEntity<T> responseEntity;
        try {
            responseEntity = restTemplate.exchange(url, method, entity, responseType);
        } catch (RestClientResponseException e) {
            throw new DownstreamServiceException(method, url, e.getRawStatusCode(), e.getResponseBodyAsString(), null);
        } catch (RestClientException e) {
            throw new DownstreamServiceException(method, url, null, null, e);
        }
        logger.info("Got response with status code " + responseEntity.getStatusCode());
        return responseEntity;
    }
}
