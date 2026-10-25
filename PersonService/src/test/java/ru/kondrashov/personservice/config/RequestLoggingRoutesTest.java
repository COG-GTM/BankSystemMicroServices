package ru.kondrashov.personservice.config;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.kondrashov.personservice.controllers.PeopleController;
import ru.kondrashov.personservice.services.PeopleService;
import ru.kondrashov.personservice.utils.PeopleMapping;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestLoggingRoutesTest {

    private final Logger logger = mock(Logger.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PeopleController(mock(PeopleService.class), mock(PeopleMapping.class)))
            .addInterceptors(new RequestLoggingInterceptor(logger))
            .build();

    @Test
    void logsCollectionRouteFromMapping() throws Exception {
        mockMvc.perform(get("/v1/people").header("Host", "common-service:8080").contentType("application/json"))
                .andExpect(status().isOk());

        verify(logger).info(anyString(), eq("/v1/people"), eq("GET"), eq("common-service:8080"), eq("application/json"));
    }

    @Test
    void logsItemRouteFromMapping() throws Exception {
        mockMvc.perform(delete("/v1/people/{id}", UUID.randomUUID()).header("Host", "common-service:8080"))
                .andExpect(status().isNoContent());

        verify(logger).info(anyString(), eq("/v1/people/{id}"), eq("DELETE"), eq("common-service:8080"), eq((Object) null));
    }
}
