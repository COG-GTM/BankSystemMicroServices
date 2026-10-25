package ru.kondrashov.accountservice.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.controllers.AccountsController;
import ru.kondrashov.accountservice.controllers.BillsController;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.services.interfaces.BillsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;
import ru.kondrashov.accountservice.utils.interfaces.AdjustmentMapping;
import ru.kondrashov.accountservice.utils.interfaces.BillsMapping;
import ru.kondrashov.accountservice.utils.interfaces.PaymentsMapping;
import ru.kondrashov.accountservice.utils.interfaces.TransferMapping;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BillsController.class, AccountsController.class})
class RequestLoggingInterceptorTest {

    private static final String BILLS = "/v1/people/{personId}/accounts/{accountId}/bills";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BillsService billsService;
    @MockBean
    private BillsMapping billsMapping;
    @MockBean
    private PaymentsMapping paymentsMapping;
    @MockBean
    private AdjustmentMapping adjustmentMapping;
    @MockBean
    private TransferMapping transferMapping;
    @MockBean
    private AccountsService accountsService;
    @MockBean
    private AccountMapping accountMapping;

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private Logger interceptorLogger;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        appender = new AbstractAppender("capture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                events.add(event.toImmutable());
            }
        };
        appender.start();
        interceptorLogger = (Logger) LogManager.getLogger(RequestLoggingInterceptor.class);
        previousLevel = interceptorLogger.getLevel();
        interceptorLogger.addAppender(appender);
        interceptorLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detachAppender() {
        interceptorLogger.removeAppender(appender);
        interceptorLogger.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void logsRouteFromHandlerMappingWithRequestDetails() throws Exception {
        UUID personId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        mockMvc.perform(put(BILLS + "/{id}/adjustments", personId, accountId, billId)
                .header("Host", "common-service:8080")
                .contentType("application/json")
                .content("{\"currency\":\"USD\",\"amount\":10,\"message\":\"correction\",\"bill\":{}}"))
                .andExpect(status().isAccepted());

        assertThat(events).hasSize(1);
        LogEvent event = events.get(0);
        assertThat(event.getLoggerName()).isEqualTo(RequestLoggingInterceptor.class.getName());
        assertThat(event.getMessage().getFormattedMessage()).isEqualTo(
                "The controller \"" + BILLS + "/{id}/adjustments\" received a PUT request "
                        + "/v1/people/" + personId + "/accounts/" + accountId + "/bills/" + billId + "/adjustments"
                        + " from the server common-service:8080 with content type application/json");
    }

    @Test
    void logsOneLinePerRequestForBothControllers() throws Exception {
        mockMvc.perform(get(BILLS + "/{id}/transactions", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                .header("Host", "common-service:8080"))
                .andExpect(status().isOk());
        UUID personId = UUID.randomUUID();
        mockMvc.perform(get("/v1/people/{personId}/accounts", personId)
                .header("Host", "common-service:8080"))
                .andExpect(status().isOk());

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getMessage().getFormattedMessage())
                .startsWith("The controller \"" + BILLS + "/{id}/transactions\" received a GET request /v1/people/");
        assertThat(events.get(1).getMessage().getFormattedMessage()).isEqualTo(
                "The controller \"/v1/people/{personId}/accounts\" received a GET request /v1/people/" + personId
                        + "/accounts from the server common-service:8080 with content type null");
    }

    @Test
    void replacesControlCharactersInClientSuppliedValues() throws Exception {
        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Host", "evil\r\nINFO forged entry")
                .contentType("text/plain\u2028x"))
                .andExpect(status().isOk());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessage().getFormattedMessage())
                .endsWith("from the server evil__INFO forged entry with content type text/plain_x")
                .doesNotContain("\r", "\n", "\u2028");
    }

    @Test
    void logSafeLeavesPrintableTextAndNullUntouched() {
        assertThat(RequestLoggingInterceptor.logSafe("/v1/people/a b?c=%0A")).isEqualTo("/v1/people/a b?c=%0A");
        assertThat(RequestLoggingInterceptor.logSafe("a\tb\u0085c\u2029")).isEqualTo("a_b_c_");
        assertThat(RequestLoggingInterceptor.logSafe(null)).isNull();
    }
}
