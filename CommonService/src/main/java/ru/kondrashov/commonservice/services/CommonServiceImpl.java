package ru.kondrashov.commonservice.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
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
    @Value("${account-closure.URL}")
    private String accountClosureUrl;
    @Value("${erasure-audit.URL}")
    private String erasureAuditUrl;

    static final String ACTOR_HEADER = "X-Actor";

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
    public void createPerson(PersonResponseDTO person) throws Exception {
        logger.info("Send POST request to "+peopleUrl);
        ResponseEntity<PersonResponseDTO> responseEntity =
                restTemplate.postForEntity(peopleUrl, person,PersonResponseDTO.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }

    @Override
    public void createAccount(UUID personId, AccountResponseDTO account) throws Exception {
        logger.info("Send POST request to "+String.format(accountsUrl, personId));
        ResponseEntity<AccountResponseDTO> responseEntity =
                restTemplate.postForEntity(String.format(accountsUrl, personId), account, AccountResponseDTO.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }

    @Override
    public void createBill(UUID personId, UUID accountId, BillResponseDTO bill) throws Exception {
        logger.info("Send POST request to "+String.format(billsUrl, personId, accountId));
        ResponseEntity<BillResponseDTO> responseEntity =
                restTemplate.postForEntity(String.format(billsUrl, personId, accountId), bill, BillResponseDTO.class);
        if(responseEntity.getStatusCode() != HttpStatus.CREATED){
            logger.error(responseEntity.toString());
            throw new Exception(responseEntity.toString());
        }
        logger.info("Got response with status code "+ responseEntity.getStatusCode());
    }


    @Override
    public void updatePerson(UUID id, PersonResponseDTO personResponseDTO){
        logger.info("Send PUT request to "+String.format(personUrl,id));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(id.toString()));
        HttpEntity<PersonResponseDTO> personRequestDTOHttpEntity = new HttpEntity<>(personResponseDTO, params);
        ResponseEntity<PersonResponseDTO> responseEntity = restTemplate.exchange(String.format(personUrl,id),HttpMethod.PUT, personRequestDTOHttpEntity, PersonResponseDTO.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }

    @Override
    public void updateAccount(UUID personId, UUID accountId, AccountResponseDTO accountResponseDTO) {
        logger.info("Send PUT request to "+String.format(accountUrl, personId, accountId));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(accountId.toString()));
        HttpEntity<AccountResponseDTO> accountResponseDTOHttpEntity = new HttpEntity<>(accountResponseDTO, params);
        ResponseEntity<AccountResponseDTO> responseEntity = restTemplate.exchange(String.format(accountUrl, personId, accountId),HttpMethod.PUT, accountResponseDTOHttpEntity, AccountResponseDTO.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }


    @Override
    public void updateBill(UUID personId, UUID accountId, UUID billId, BillResponseDTO billResponseDTO) {
        logger.info("Send PUT request to "+String.format(billUrl, personId, accountId, billId));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.put("id", Collections.singletonList(billId.toString()));
        HttpEntity<BillResponseDTO> billResponseDTOHttpEntity = new HttpEntity<>(billResponseDTO, params);
        ResponseEntity<BillResponseDTO> responseEntity = restTemplate.exchange(String.format(billUrl, personId, accountId, billId), HttpMethod.PUT, billResponseDTOHttpEntity, BillResponseDTO.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }


    /**
     * Customer erasure workflow. Cross-service atomicity is not possible, so every step is idempotent and the
     * workflow can be re-run until it completes:
     * 1. record a STARTED audit event (nothing destructive happens if the audit store is unavailable);
     * 2. verify every bill of every open account has a zero balance, otherwise record REJECTED and stop
     *    before changing anything;
     * 3. soft-close each open account (bills and transactions are retained; already closed accounts are skipped);
     * 4. anonymise the person in PersonService, which writes the COMPLETED audit event in the same transaction.
     * Any downstream failure records a FAILED audit event and leaves the workflow safe to retry.
     */
    @Override
    public void deletePerson(UUID id, String actor) {
        logger.info("Erasure of person "+id+" requested by "+actor);
        recordErasureEvent(id, actor, "STARTED", null);
        try {
            Collection<AccountRequestDTO> accounts = getAccountsByPerson(id);

            List<String> unsettled = new ArrayList<>();
            for (AccountRequestDTO account : accounts) {
                for (BillRequestDTO bill : getBillsByAccountId(id, account.getId())) {
                    if (bill.getAmount() != null && bill.getAmount().signum() != 0) {
                        unsettled.add("bill " + bill.getId() + " of account " + account.getId());
                    }
                }
            }
            if (!unsettled.isEmpty()) {
                String detail = "Non-zero balance on " + String.join(", ", unsettled);
                recordErasureEvent(id, actor, "REJECTED", detail);
                logger.warn("Erasure of person "+id+" by "+actor+" rejected: "+detail);
                throw new PersonErasureException("Person " + id + " cannot be erased: " + detail);
            }

            for (AccountRequestDTO account : accounts) {
                closeAccount(id, account.getId());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set(ACTOR_HEADER, actor);
            ResponseEntity<Void> responseEntity = restTemplate.exchange(String.format(personUrl,id), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            logger.info("Erasure of person "+id+" by "+actor+" completed with status code "+responseEntity.getStatusCode());
        } catch (RestClientException e) {
            String detail = "Erasure interrupted, safe to retry: " + e.getMessage();
            try {
                recordErasureEvent(id, actor, "FAILED", detail);
            } catch (RestClientException auditFailure) {
                logger.error("Could not record FAILED erasure audit event for person "+id+" by "+actor, auditFailure);
            }
            logger.error("Erasure of person "+id+" by "+actor+" failed", e);
            throw new PersonErasureException("Erasure of person " + id + " did not complete and can be retried", e);
        }
    }

    private void closeAccount(UUID personId, UUID accountId) {
        logger.info("Send POST request to "+String.format(accountClosureUrl, personId, accountId));
        ResponseEntity<Void> responseEntity = restTemplate.exchange(String.format(accountClosureUrl, personId, accountId), HttpMethod.POST, null, Void.class);
        logger.info("Got response with status code "+responseEntity.getStatusCode());
    }

    private void recordErasureEvent(UUID personId, String actor, String outcome, String detail) {
        Map<String, String> event = new HashMap<>();
        event.put("actor", actor);
        event.put("outcome", outcome);
        event.put("detail", detail);
        restTemplate.postForEntity(String.format(erasureAuditUrl, personId), event, Void.class);
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
