package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpStatus;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AccountServiceBasicAuthInterceptorTest {

    private final AccountServiceBasicAuthInterceptor interceptor =
            new AccountServiceBasicAuthInterceptor("http://localhost:8081", "svc", "secret");

    private final ClientHttpRequestExecution execution =
            (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    @Test
    void addsBasicAuthToAccountServiceRequests() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.PUT, URI.create("http://localhost:8081/v1/people/1/accounts"));
        interceptor.intercept(request, new byte[0], execution);
        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic c3ZjOnNlY3JldA==");
    }

    @Test
    void doesNotSendCredentialsToOtherServices() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:8082/v1/people"));
        interceptor.intercept(request, new byte[0], execution);
        assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
