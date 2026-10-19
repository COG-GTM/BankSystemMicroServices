package ru.kondrashov.accountservice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@SpringBootApplication
@EnableSwagger2
@EnableScheduling
public class AccountServiceApplication {

    static final Logger LOGGER = LogManager.getRootLogger();

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
        LOGGER.info("Context is up");
    }

}
