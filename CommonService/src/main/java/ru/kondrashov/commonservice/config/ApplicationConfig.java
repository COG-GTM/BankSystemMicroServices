package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${app.downstream.username}") String username,
                                     @Value("${app.downstream.password}") String password) {
        return restTemplateBuilder.basicAuthentication(username, password).build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}