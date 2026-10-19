package ru.kondrashov.personservice.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.personservice.exception.ErasurePreconditionUnavailableException;

import java.util.UUID;

@Component
public class HttpOpenAccountsVerifier implements OpenAccountsVerifier {

    private final RestTemplate restTemplate;
    private final String accountsUrl;

    public HttpOpenAccountsVerifier(RestTemplate restTemplate,
                                    @Value("${accounts.URL:http://localhost:8081/v1/people/%s/accounts}") String accountsUrl) {
        this.restTemplate = restTemplate;
        this.accountsUrl = accountsUrl;
    }

    @Override
    public boolean hasOpenAccounts(UUID personId) {
        try {
            Object[] accounts = restTemplate.getForObject(String.format(accountsUrl, personId), Object[].class);
            if (accounts == null) {
                throw new ErasurePreconditionUnavailableException("Could not verify accounts of person " + personId + ": empty response");
            }
            return accounts.length > 0;
        } catch (RestClientException e) {
            throw new ErasurePreconditionUnavailableException("Could not verify accounts of person " + personId + ": " + e.getMessage());
        }
    }
}
