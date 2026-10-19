package ru.kondrashov.accountservice.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.SwaggerController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SwaggerExposureTest {

    @Nested
    @WebMvcTest(controllers = SwaggerController.class)
    @Import(SwaggerConfig.class)
    class DefaultProfile {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void apiDocsAreNotPublished() throws Exception {
            mockMvc.perform(get("/v2/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-resources")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-resources/configuration/ui")).andExpect(status().isNotFound());
        }

        @Test
        void swaggerUiIsNotServed() throws Exception {
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
            mockMvc.perform(get("/webjars/springfox-swagger-ui/swagger-ui-bundle.js"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void swaggerRedirectIsNotRegistered() throws Exception {
            mockMvc.perform(get("/v1")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @WebMvcTest(controllers = SwaggerController.class)
    @Import(SwaggerConfig.class)
    @ActiveProfiles(SwaggerConfig.SWAGGER_PROFILE)
    class DevProfile {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void apiDocsArePublished() throws Exception {
            mockMvc.perform(get("/v2/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("\"swagger\":\"2.0\"")))
                    .andExpect(content().string(not(containsString("basic-error-controller"))));
        }

        @Test
        void swaggerUiIsServed() throws Exception {
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isOk());
            mockMvc.perform(get("/v1")).andExpect(redirectedUrl("swagger-ui.html"));
        }
    }
}
