package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationSpringConfig {

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}
