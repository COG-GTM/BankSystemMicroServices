package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonServiceAuthInterceptorTest {

    private static final String PASSWORD = "service-password-0001";
    private final PersonServiceAuthInterceptor interceptor =
            new PersonServiceAuthInterceptor("http://localhost:8082/v1/people", "common-service", PASSWORD);
    private final ClientHttpRequestExecution execution = (request, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK);

    private HttpHeaders send(String url) throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create(url));
        interceptor.intercept(request, new byte[0], execution);
        return request.getHeaders();
    }

    @Test
    void addsBasicCredentialsToPersonServiceRequests() throws Exception {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString(("common-service:" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        assertThat(send("http://localhost:8082/v1/people").getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expected);
        assertThat(send("http://LOCALHOST:8082/v1/people/c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f").getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo(expected);
    }

    @Test
    void neverSendsCredentialsToOtherServices() throws Exception {
        assertThat(send("http://localhost:8081/v1/people/c5a6757f-fec4-41f3-a6a6-eb03d70c0d6f/accounts").containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(send("http://evil.example:8082/v1/people").containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(send("https://localhost:8082/v1/people").containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void refusesToStartWithoutStrongCredentials() {
        assertThatThrownBy(() -> new PersonServiceAuthInterceptor("http://localhost:8082/v1/people", "common-service", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PersonServiceAuthInterceptor("http://localhost:8082/v1/people", "common-service", "12345"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PersonServiceAuthInterceptor("http://localhost:8082/v1/people", " ", PASSWORD))
                .isInstanceOf(IllegalStateException.class);
    }
}
