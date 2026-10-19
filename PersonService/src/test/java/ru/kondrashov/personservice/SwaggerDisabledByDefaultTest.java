package ru.kondrashov.personservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import springfox.documentation.spring.web.plugins.Docket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "/hibernate.properties")
class SwaggerDisabledByDefaultTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void docketIsNotRegistered() {
        assertThat(applicationContext.getBeansOfType(Docket.class)).isEmpty();
    }

    @Test
    void swaggerEndpointsReturnNotFound() throws Exception {
        String[] paths = {
                "/v1",
                "/swagger-ui.html",
                "/webjars/springfox-swagger-ui/springfox.js",
                "/swagger-resources",
                "/swagger-resources/configuration/ui",
                "/v2/api-docs"
        };
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }
}
