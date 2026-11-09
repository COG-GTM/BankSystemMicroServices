package ru.kondrashov.commonservice.observability;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_HEADER;
import static ru.kondrashov.commonservice.observability.CorrelationIdFilter.CORRELATION_ID_MDC_KEY;

/**
 * Forwards the current correlation ID to downstream services called through RestTemplate.
 */
public class CorrelationIdPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId != null && !request.getHeaders().containsKey(CORRELATION_ID_HEADER)) {
            request.getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
