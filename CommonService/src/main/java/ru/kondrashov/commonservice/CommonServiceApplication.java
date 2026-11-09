package ru.kondrashov.commonservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration;

@SpringBootApplication(exclude = IntegrationAutoConfiguration.class)
public class CommonServiceApplication {

    static final Logger LOGGER = LoggerFactory.getLogger(CommonServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CommonServiceApplication.class, args);
        LOGGER.info("Context is up");
    }

}
