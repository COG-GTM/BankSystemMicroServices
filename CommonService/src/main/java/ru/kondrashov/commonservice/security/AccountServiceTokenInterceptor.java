package ru.kondrashov.commonservice.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Attaches a short-lived HS256 service token (aud=account-service, scope=accounts:admin)
 * to requests for AccountService's person/account API. Other hosts and paths are left untouched.
 */
public class AccountServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    public static final String AUDIENCE = "account-service";
    public static final String SCOPE = "accounts:admin";
    private static final String SUBJECT = "common-service";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MIN_SECRET_BYTES = 32;

    private final String accountServiceAuthority;
    private final String accountServicePathPrefix;
    private final MACSigner signer;
    private final Clock clock;

    public AccountServiceTokenInterceptor(String accountServiceUrl, String hmacSecret) {
        this(accountServiceUrl, hmacSecret, Clock.systemUTC());
    }

    AccountServiceTokenInterceptor(String accountServiceUrl, String hmacSecret, Clock clock) {
        if (hmacSecret == null || hmacSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "account-service.security.jwt.hmac-secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        try {
            this.signer = new MACSigner(hmacSecret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid account-service.security.jwt.hmac-secret", e);
        }
        int placeholder = accountServiceUrl.indexOf("%s");
        URI accountServiceUri = URI.create(placeholder < 0 ? accountServiceUrl : accountServiceUrl.substring(0, placeholder));
        this.accountServiceAuthority = accountServiceUri.getAuthority();
        this.accountServicePathPrefix = accountServiceUri.getRawPath();
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (accountServiceAuthority.equalsIgnoreCase(request.getURI().getAuthority())
                && request.getURI().getRawPath() != null
                && request.getURI().getRawPath().startsWith(accountServicePathPrefix)) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + createToken());
        }
        return execution.execute(request, body);
    }

    String createToken() {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(SUBJECT)
                .audience(AUDIENCE)
                .claim("scope", SCOPE)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(TTL)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign AccountService token", e);
        }
        return jwt.serialize();
    }
}
