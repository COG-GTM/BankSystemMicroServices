package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${account-service.username:}") String accountServiceUsername,
                                     @Value("${account-service.password:}") String accountServicePassword,
                                     @Value("${account-service.base-url:http://localhost:8081}") String accountServiceBaseUrl) {
        RestTemplate restTemplate = restTemplateBuilder.build();
        if (StringUtils.hasText(accountServiceUsername) && StringUtils.hasText(accountServicePassword)) {
            restTemplate.getInterceptors().add(new AccountServiceBasicAuthInterceptor(
                    accountServiceBaseUrl, accountServiceUsername, accountServicePassword));
        }
        return restTemplate;
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}