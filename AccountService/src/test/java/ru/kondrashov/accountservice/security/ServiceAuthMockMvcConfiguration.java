package ru.kondrashov.accountservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every MockMvc request in tests carry the service token, as CommonService does.
 * Registered for @WebMvcTest/@AutoConfigureMockMvc via META-INF/spring.factories.
 */
@Configuration(proxyBeanMethods = false)
public class ServiceAuthMockMvcConfiguration {

    @Bean
    public MockMvcBuilderCustomizer serviceAuthDefaultHeader(@Value("${service.auth.token}") String token) {
        return builder -> builder.defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
