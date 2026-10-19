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
                                     @Value("${account-service.URL}") String accountServiceUrl,
                                     @Value("${account-service.client.username}") String accountServiceUsername,
                                     @Value("${account-service.client.password}") String accountServicePassword,
                                     @Value("${accounts.URL}") String accountsUrl,
                                     @Value("${account.URL}") String accountUrl,
                                     @Value("${bills.URL}") String billsUrl,
                                     @Value("${bill.URL}") String billUrl,
                                     @Value("${transactions.URL}") String transactionsUrl) {
        requireUnderAccountService(accountServiceUrl, accountsUrl, accountUrl, billsUrl, billUrl, transactionsUrl);
        return restTemplateBuilder
                .additionalInterceptors(accountServiceAuthentication(accountServiceUrl, accountServiceUsername, accountServicePassword))
                .build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }

    static ClientHttpRequestInterceptor accountServiceAuthentication(String accountServiceUrl, String username, String password) {
        UriComponents accountService = UriComponentsBuilder.fromUriString(accountServiceUrl).build();
        if (!"https".equalsIgnoreCase(accountService.getScheme()) && !isLoopback(accountService.getHost())) {
            throw new IllegalStateException("account-service.URL must use https for non-local hosts, "
                    + "otherwise AccountService credentials would be sent in clear text: " + accountServiceUrl);
        }
        String basePath = accountService.getPath() == null ? "" : accountService.getPath().replaceAll("/+$", "");
        return (request, body, execution) -> {
            URI uri = request.getURI();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (Objects.equals(uri.getScheme(), accountService.getScheme())
                    && Objects.equals(uri.getHost(), accountService.getHost())
                    && uri.getPort() == accountService.getPort()
                    && (path.equals(basePath) || path.startsWith(basePath + "/"))
                    && !hasDotSegment(path)) {
                request.getHeaders().setBasicAuth(username, password);
            }
            return execution.execute(request, body);
        };
    }

    static void requireUnderAccountService(String accountServiceUrl, String... endpointUrls) {
        String base = accountServiceUrl.replaceAll("/+$", "") + "/";
        for (String endpointUrl : endpointUrls) {
            if (!endpointUrl.startsWith(base)) {
                throw new IllegalStateException("AccountService endpoint " + endpointUrl
                        + " must be under account-service.URL (" + accountServiceUrl
                        + "), otherwise requests to it are sent without credentials");
            }
        }
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLoopback(String host) {
        return host != null
                && (host.equalsIgnoreCase("localhost")
                || host.matches("127(\\.\\d{1,3}){3}")
                || host.equals("::1") || host.equals("[::1]"));
    }
}
