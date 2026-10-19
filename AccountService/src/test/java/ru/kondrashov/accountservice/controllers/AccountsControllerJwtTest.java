package ru.kondrashov.accountservice.controllers;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.kondrashov.accountservice.config.ApplicationSpringConfig;
import ru.kondrashov.accountservice.config.SecurityConfig;
import ru.kondrashov.accountservice.security.PersonAccess;
import ru.kondrashov.accountservice.services.interfaces.AccountsService;
import ru.kondrashov.accountservice.utils.interfaces.AccountMapping;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real HS256 JwtDecoder (signature, expiry and audience validation). */
@WebMvcTest(controllers = AccountsController.class)
@Import({SecurityConfig.class, PersonAccess.class, ApplicationSpringConfig.class})
@TestPropertySource(properties = "account-service.security.jwt.hmac-secret=" + AccountsControllerJwtTest.SECRET)
class AccountsControllerJwtTest {

    static final String SECRET = "test-only-hmac-secret-0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountsService accountsService;
    @MockBean
    private AccountMapping accountMapping;

    @Test
    void validServiceTokenIsAccepted() throws Exception {
        String token = sign(SECRET, claims("account-service", Instant.now().plusSeconds(60))
                .claim("scope", "accounts:admin").build());

        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void validPersonTokenIsLimitedToThatPerson() throws Exception {
        UUID personId = UUID.randomUUID();
        String token = sign(SECRET, claims("account-service", Instant.now().plusSeconds(60))
                .claim("person_id", personId.toString()).build());

        mockMvc.perform(get("/v1/people/{personId}/accounts", personId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenSignedWithOtherKeyIsRejected() throws Exception {
        String token = sign("attacker-controlled-secret-0123456789abcdef", claims("account-service", Instant.now().plusSeconds(60))
                .claim("scope", "accounts:admin").build());

        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = sign(SECRET, claims("account-service", Instant.now().minusSeconds(3600))
                .claim("scope", "accounts:admin").build());

        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        String token = sign(SECRET, claims("person-service", Instant.now().plusSeconds(60))
                .claim("scope", "accounts:admin").build());

        mockMvc.perform(get("/v1/people/{personId}/accounts", UUID.randomUUID())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private static JWTClaimsSet.Builder claims(String audience, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject("common-service")
                .audience(audience)
                .issueTime(Date.from(expiresAt.minusSeconds(120)))
                .expirationTime(Date.from(expiresAt));
    }

    private static String sign(String secret, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
