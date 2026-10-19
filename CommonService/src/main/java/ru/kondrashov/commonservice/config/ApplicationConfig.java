package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Objects;

@Configuration
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${accounts.URL}") String accountsUrl,
                                     @Value("${account-service.client.username}") String accountServiceUsername,
                                     @Value("${account-service.client.password}") String accountServicePassword) {
        return restTemplateBuilder
                .additionalInterceptors(accountServiceAuthentication(accountsUrl, accountServiceUsername, accountServicePassword))
                .build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }

    private ClientHttpRequestInterceptor accountServiceAuthentication(String accountsUrl, String username, String password) {
        UriComponents accountService = UriComponentsBuilder.fromUriString(accountsUrl).build();
        return (request, body, execution) -> {
            URI uri = request.getURI();
            if (Objects.equals(uri.getScheme(), accountService.getScheme())
                    && Objects.equals(uri.getHost(), accountService.getHost())
                    && uri.getPort() == accountService.getPort()) {
                request.getHeaders().setBasicAuth(username, password);
            }
            return execution.execute(request, body);
        };
    }
}
