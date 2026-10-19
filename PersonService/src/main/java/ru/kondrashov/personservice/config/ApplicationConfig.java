package ru.kondrashov.personservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    @Bean
    public GroupedOpenApi api()
    {
        return GroupedOpenApi.builder()
                .group("person-service")
                .pathsToMatch("/v1/**")
                .build();
    }

    @Bean
    public OpenAPI apiInfo()
    {
        return new OpenAPI().info(new Info()
                .title("Person service")
                .description("")
                .version("version-1")
                .termsOfService("API TOS")
                .contact(new Contact().email("mikhail.a.kondrashov@gmail.com"))
                .license(new License().name("API License").url("API License URL")));
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}
