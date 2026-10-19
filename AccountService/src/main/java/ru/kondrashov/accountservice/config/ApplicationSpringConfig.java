package ru.kondrashov.accountservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
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

    @Bean
    public OpenAPI api()
    {
        return new OpenAPI().info(new Info()
                .title("Account service")
                .version("version-1")
                .contact(new Contact().email("mikhail.a.kondrashov@gmail.com")));
    }
}
