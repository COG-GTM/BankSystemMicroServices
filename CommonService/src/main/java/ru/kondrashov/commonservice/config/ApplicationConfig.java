package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import ru.kondrashov.commonservice.security.AccountServiceTokenInterceptor;

@Configuration
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${accounts.URL}") String accountsUrl,
                                     @Value("${account-service.security.jwt.hmac-secret}") String accountServiceSecret) {
        return restTemplateBuilder
                .additionalInterceptors(new AccountServiceTokenInterceptor(accountsUrl, accountServiceSecret))
                .build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}
