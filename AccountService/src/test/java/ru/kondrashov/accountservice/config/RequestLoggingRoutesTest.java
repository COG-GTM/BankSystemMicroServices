package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.kondrashov.accountservice.controllers.AccountsController;
import ru.kondrashov.accountservice.controllers.BillsController;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

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
            .standaloneSetup(
                    new AccountsController(mock(AccountsService.class), mock(AccountMapping.class)),
                    new BillsController(mock(BillsService.class), mock(BillsMapping.class), mock(PaymentsMapping.class),
                            mock(AdjustmentMapping.class), mock(TransferMapping.class)))
            .addInterceptors(new RequestLoggingInterceptor(logger))
            .build();

    @Test
    void logsAccountRouteFromMapping() throws Exception {
        mockMvc.perform(delete("/v1/people/{personId}/accounts/{id}", UUID.randomUUID(), UUID.randomUUID())
                        .header("Host", "common-service:8080"))
                .andExpect(status().isNoContent());

        verify(logger).info(anyString(), eq("/v1/people/{personId}/accounts/{id}"), eq("DELETE"),
                eq("common-service:8080"), eq((Object) null));
    }

    @Test
    void logsBillRouteFromMapping() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                        .header("Host", "common-service:8080")
                        .contentType("application/json"))
                .andExpect(status().isOk());

        verify(logger).info(anyString(), eq("/v1/people/{personId}/accounts/{accountId}/bills/{id}/transactions"),
                eq("GET"), eq("common-service:8080"), eq("application/json"));
    }
}
