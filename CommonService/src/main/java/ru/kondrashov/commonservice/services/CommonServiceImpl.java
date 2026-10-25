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
import ru.kondrashov.commonservice.exceptions.DownstreamServiceException;

import java.util.*;
import java.util.function.Supplier;

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
        String url = String.format(personUrl, uuid);
        return send(HttpMethod.GET, url, null, () -> restTemplate.getForEntity(url, PersonRequestDTO.class)).getBody();
    }

    @Override
    public Collection<PersonRequestDTO> getPeople() {
        return send(HttpMethod.GET, peopleUrl, null, () -> restTemplate.exchange(
                peopleUrl,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<PersonRequestDTO>>() {}
        )).getBody();
    }

    @Override
    public Collection<AccountRequestDTO> getAccountsByPerson(UUID id) {
        String url = String.format(accountsUrl, id);
        return send(HttpMethod.GET, url, null, () -> restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<AccountRequestDTO>>() {}
        )).getBody();
    }

    @Override
    public Collection<BillRequestDTO> getBillsByAccountId(UUID personId, UUID accountId) {
        String url = String.format(billsUrl, personId, accountId);
        return send(HttpMethod.GET, url, null, () -> restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<BillRequestDTO>>() {}
        )).getBody();
    }

    @Override
    public Collection<FinancialTransactionRequestDTO> getFinancialTransactionsByBillId(UUID personId, UUID accountId, UUID billId) {
        String url = String.format(transactionsUrl, personId, accountId, billId);
        return send(HttpMethod.GET, url, null, () -> restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<FinancialTransactionRequestDTO>>() {}
        )).getBody();
    }


    @Override
    public void createPerson(PersonResponseDTO person) {
        send(HttpMethod.POST, peopleUrl, HttpStatus.CREATED,
                () -> restTemplate.postForEntity(peopleUrl, person, PersonResponseDTO.class));
    }

    @Override
    public void createAccount(UUID personId, AccountResponseDTO account) {
        String url = String.format(accountsUrl, personId);
        send(HttpMethod.POST, url, HttpStatus.CREATED,
                () -> restTemplate.postForEntity(url, account, AccountResponseDTO.class));
    }

    @Override
    public void createBill(UUID personId, UUID accountId, BillResponseDTO bill) {
        String url = String.format(billsUrl, personId, accountId);
        send(HttpMethod.POST, url, HttpStatus.CREATED,
                () -> restTemplate.postForEntity(url, bill, BillResponseDTO.class));
    }


    @Override
    public void updatePerson(UUID id, PersonResponseDTO personResponseDTO){
        String url = String.format(personUrl, id);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(id.toString()));
        HttpEntity<PersonResponseDTO> personRequestDTOHttpEntity = new HttpEntity<>(personResponseDTO, params);
        send(HttpMethod.PUT, url, null,
                () -> restTemplate.exchange(url, HttpMethod.PUT, personRequestDTOHttpEntity, PersonResponseDTO.class));
    }

    @Override
    public void updateAccount(UUID personId, UUID accountId, AccountResponseDTO accountResponseDTO) {
        String url = String.format(accountUrl, personId, accountId);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        HttpEntity<AccountResponseDTO> accountResponseDTOHttpEntity = new HttpEntity<>(accountResponseDTO, params);
        send(HttpMethod.PUT, url, null,
                () -> restTemplate.exchange(url, HttpMethod.PUT, accountResponseDTOHttpEntity, AccountResponseDTO.class));
    }


    @Override
    public void updateBill(UUID personId, UUID accountId, UUID billId, BillResponseDTO billResponseDTO) {
        String url = String.format(billUrl, personId, accountId, billId);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(billId.toString()));
        HttpEntity<BillResponseDTO> billResponseDTOHttpEntity = new HttpEntity<>(billResponseDTO, params);
        send(HttpMethod.PUT, url, null,
                () -> restTemplate.exchange(url, HttpMethod.PUT, billResponseDTOHttpEntity, BillResponseDTO.class));
    }


    @Override
    public void deletePerson(UUID id) {
        for (AccountRequestDTO account:getAccountsByPerson(id)) {
            deleteAccount(id,account.getId());
        }
        String url = String.format(personUrl, id);
        send(HttpMethod.DELETE, url, null, () -> restTemplate.exchange(url, HttpMethod.DELETE, null, Object.class));
    }

    @Override
    public void deleteAccount(UUID personId, UUID accountId) {
        String url = String.format(accountUrl, personId, accountId);
        send(HttpMethod.DELETE, url, null, () -> restTemplate.exchange(url, HttpMethod.DELETE, null, Object.class));
    }

    @Override
    public void deleteBill(UUID personId, UUID accountId, UUID billId) {
        String url = String.format(billUrl, personId, accountId, billId);
        send(HttpMethod.DELETE, url, null, () -> restTemplate.exchange(url, HttpMethod.DELETE, null, Object.class));
    }

    private <T> ResponseEntity<T> send(HttpMethod method, String url, HttpStatus expectedStatus, Supplier<ResponseEntity<T>> request) {
        logger.info("Send "+method+" request to "+url);
        ResponseEntity<T> responseEntity;
        try {
            responseEntity = request.get();
        } catch (RestClientResponseException e) {
            throw new DownstreamServiceException(method, url, HttpStatus.resolve(e.getRawStatusCode()), e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new DownstreamServiceException(method, url, null, null, e);
        }
        if (expectedStatus != null && responseEntity.getStatusCode() != expectedStatus) {
            throw new DownstreamServiceException(method, url, responseEntity.getStatusCode(), null, null);
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
        return responseEntity;
    }
}
