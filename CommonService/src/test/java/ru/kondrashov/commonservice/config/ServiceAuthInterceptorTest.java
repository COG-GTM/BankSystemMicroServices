package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ServiceAuthInterceptorTest {

    private RestTemplate restTemplate(boolean requireTls) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Collections.singletonList(new ServiceAuthInterceptor("tok-123", requireTls)));
        return restTemplate;
    }

    @Test
    void addsBearerTokenToHttpsRequests() {
        RestTemplate restTemplate = restTemplate(true);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://people.internal/v1/people"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-123"))
                .andRespond(withSuccess());

        restTemplate.getForObject("https://people.internal/v1/people", String.class);

        server.verify();
    }

    @Test
    void refusesPlainHttpWhenTlsRequired() {
        RestTemplate restTemplate = restTemplate(true);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        assertThatThrownBy(() -> restTemplate.getForObject("http://people.internal/v1/people", String.class))
                .isInstanceOf(ServiceAuthInterceptor.InsecureDownstreamUrlException.class);
        server.verify();
    }

    @Test
    void allowsPlainHttpOnlyWhenTlsExplicitlyDisabled() {
        RestTemplate restTemplate = restTemplate(false);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://localhost:8082/v1/people"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-123"))
                .andRespond(withSuccess());

        restTemplate.getForObject("http://localhost:8082/v1/people", String.class);

        server.verify();
    }
}
