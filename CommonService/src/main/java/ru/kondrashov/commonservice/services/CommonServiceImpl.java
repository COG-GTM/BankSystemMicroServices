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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.controllers.dto.*;
import ru.kondrashov.commonservice.exceptions.PersonDeletionException;

import java.net.ConnectException;
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
     * Deletes a person and everything AccountService holds for them, in this order:
     * <ol>
     *     <li>all of the person's accounts, in one all-or-nothing call to AccountService;</li>
     *     <li>the person record in PersonService.</li>
     * </ol>
     * Accounts go first so a failure never leaves accounts pointing at a person that no longer exists.
     * The two services do not share a transaction: if step 2 fails, the accounts stay deleted and a
     * {@link PersonDeletionException} reports that. An error status means the step did not happen; no
     * response at all means its outcome is unknown. Both steps are idempotent (no accounts and an absent
     * person both count as success), so repeating the delete finishes the job either way.
     */
    @Override
    public void deletePerson(UUID id) {
        logger.info("Deleting person "+id+": step 1/2 delete accounts, step 2/2 delete person");
        deleteAccountsOfPerson(id);
        deletePersonRecord(id);
        logger.info("Deleted person "+id);
    }

    private void deleteAccountsOfPerson(UUID personId) {
        String url = String.format(accountsUrl, personId);
        logger.info("Step 1/2: send DELETE request to "+url);
        try {
            ResponseEntity<Object> responseEntity = restTemplate.exchange(url, HttpMethod.DELETE, null, Object.class);
            logger.info("Step 1/2: accounts of person "+personId+" deleted, status code "+responseEntity.getStatusCode());
        } catch (HttpStatusCodeException e) {
            logger.error("Step 1/2: AccountService refused to delete accounts of person "+personId
                    +" with status "+e.getStatusCode()+", nothing was deleted", e);
            throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_ACCOUNTS, true, e);
        } catch (RestClientException e) {
            if (neverReachedService(e)) {
                logger.error("Step 1/2: could not connect to AccountService, nothing was deleted", e);
                throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_ACCOUNTS, true, e);
            }
            logger.error("Step 1/2: no response from AccountService deleting accounts of person "+personId
                    +", accounts may or may not be deleted", e);
            throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_ACCOUNTS, false, e);
        }
    }

    private void deletePersonRecord(UUID personId) {
        String url = String.format(personUrl, personId);
        logger.info("Step 2/2: send DELETE request to "+url);
        try {
            ResponseEntity<Object> responseEntity = restTemplate.exchange(url, HttpMethod.DELETE, null, Object.class);
            logger.info("Step 2/2: person "+personId+" deleted, status code "+responseEntity.getStatusCode());
        } catch (HttpClientErrorException.NotFound e) {
            logger.info("Step 2/2: person "+personId+" does not exist in PersonService, nothing left to delete");
        } catch (HttpStatusCodeException e) {
            logger.error("Step 2/2: PersonService refused to delete person "+personId+" with status "
                    +e.getStatusCode()+" after its accounts were deleted", e);
            throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_PERSON, true, e);
        } catch (RestClientException e) {
            if (neverReachedService(e)) {
                logger.error("Step 2/2: could not connect to PersonService after the accounts of person "+personId
                        +" were deleted, person record still exists", e);
                throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_PERSON, true, e);
            }
            logger.error("Step 2/2: no response from PersonService deleting person "+personId
                    +" after its accounts were deleted, person may or may not be deleted", e);
            throw new PersonDeletionException(personId, PersonDeletionException.Step.DELETE_PERSON, false, e);
        }
    }

    private static boolean neverReachedService(RestClientException e) {
        return e.getCause() instanceof ConnectException;
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
