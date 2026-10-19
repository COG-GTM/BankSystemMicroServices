package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     @Value("${people.URL}") String peopleUrl,
                                     @Value("${person.URL}") String personUrl,
                                     @Value("${person-service.client.username}") String personServiceUsername,
                                     @Value("${person-service.client.password}") String personServicePassword) {
        return restTemplateBuilder
                .additionalInterceptors(new PersonServiceAuthInterceptor(
                        Arrays.asList(peopleUrl, String.format(personUrl, "id")),
                        personServiceUsername, personServicePassword))
                .build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}