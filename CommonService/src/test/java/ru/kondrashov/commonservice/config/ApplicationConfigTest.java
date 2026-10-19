package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpStatus;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationConfigTest {

    private static final String BASE_URL = "http://localhost:8081/v1/people";

    private final ClientHttpRequestInterceptor interceptor =
            ApplicationConfig.accountServiceAuthentication(BASE_URL, "client", "secret");

    @Test
    void addsCredentialsToAccountServiceRequests() throws Exception {
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/1/accounts")).isNotNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people")).isNotNull();
    }

    @Test
    void doesNotAddCredentialsOutsideAccountServiceBasePath() throws Exception {
        assertThat(authorizationSentTo("http://localhost:8081/other-app/v1/people")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/peoplex")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/../admin")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/..")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/.")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/%2e%2e")).isNull();
        assertThat(authorizationSentTo("http://localhost:8081/v1/people/..%2Fadmin")).isNull();
        assertThat(authorizationSentTo("http://localhost:8082/v1/people/1")).isNull();
        assertThat(authorizationSentTo("https://localhost:8081/v1/people/1")).isNull();
    }

    @Test
    void rejectsPlainHttpForNonLoopbackHosts() {
        assertThatThrownBy(() -> ApplicationConfig.accountServiceAuthentication(
                "http://account-service:8081/v1/people", "client", "secret"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ApplicationConfig.accountServiceAuthentication(
                "https://account-service:8443/v1/people", "client", "secret")).isNotNull();
        assertThat(ApplicationConfig.accountServiceAuthentication(
                "http://127.0.0.1:8081/v1/people", "client", "secret")).isNotNull();
    }

    @Test
    void rejectsAccountServiceEndpointsOutsideBaseUrl() {
        ApplicationConfig.requireUnderAccountService(BASE_URL, BASE_URL + "/%s/accounts");
        assertThatThrownBy(() -> ApplicationConfig.requireUnderAccountService(
                BASE_URL, "https://accounts.internal/v1/people/%s/accounts"))
                .isInstanceOf(IllegalStateException.class);
    }

    private String authorizationSentTo(String url) throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create(url));
        ClientHttpRequestExecution execution =
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        interceptor.intercept(request, new byte[0], execution);
        return request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    }
}
