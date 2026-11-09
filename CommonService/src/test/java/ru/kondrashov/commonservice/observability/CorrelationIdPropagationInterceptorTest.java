package ru.kondrashov.commonservice.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

class CorrelationIdPropagationInterceptorTest {

    private static final String URL = "http://downstream/v1/people";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsCorrelationIdFromMdcToDownstreamCall() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CORRELATION_ID_HEADER, "abc-123"))
                .andRespond(withSuccess());

        MDC.put(CORRELATION_ID_MDC_KEY, "abc-123");
        restTemplate.getForObject(URL, String.class);

        server.verify();
    }

    @Test
    void doesNotAddHeaderWhenNoCorrelationIdInMdc() {
        RestTemplate restTemplate = restTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist(CORRELATION_ID_HEADER))
                .andRespond(withSuccess());

        restTemplate.getForObject(URL, String.class);

        server.verify();
    }

    private static RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new CorrelationIdPropagationInterceptor());
        return restTemplate;
    }
}
