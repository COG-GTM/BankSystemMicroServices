package ru.kondrashov.commonservice.security;

import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceTokenInterceptorTest {

    private static final String SECRET = "test-only-hmac-secret-0123456789abcdef";
    private static final String ACCOUNTS_URL = "http://localhost:8081/v1/people/%s/accounts";

    private final AccountServiceTokenInterceptor interceptor = new AccountServiceTokenInterceptor(ACCOUNTS_URL, SECRET);

    @Test
    void addsSignedServiceTokenToAccountServiceRequests() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:8081/v1/people/1/accounts"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(header).startsWith("Bearer ");
        SignedJWT jwt = SignedJWT.parse(header.substring("Bearer ".length()));
        assertThat(jwt.verify(new MACVerifier(SECRET.getBytes(StandardCharsets.UTF_8)))).isTrue();
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly("account-service");
        assertThat(claims.getStringClaim("scope")).isEqualTo("accounts:admin");
        assertThat(claims.getExpirationTime()).isAfter(claims.getIssueTime());
    }

    @Test
    void doesNotSendTokenToOtherServices() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:8082/v1/people"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void doesNotSendTokenToOtherPathsOnAccountServiceHost() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://localhost:8081/actuator/env"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void sendsTokenToNestedBillEndpoints() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.PUT,
                URI.create("http://localhost:8081/v1/people/1/accounts/2/bills/3/payments"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new AccountServiceTokenInterceptor(ACCOUNTS_URL, "short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
