package ru.kondrashov.accountservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * All {@code /v1/**} endpoints require a bearer JWT. Person-scoped paths additionally
 * require the caller to be authorized for the {@code personId} in the path (see PersonAccess).
 *
 * Tokens are verified with the HS256 secret shared with CommonService
 * ({@code account-service.security.jwt.hmac-secret}); without it the application refuses to start.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String AUDIENCE = "account-service";
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                    .antMatchers("/v1/people/{personId}", "/v1/people/{personId}/**")
                        .access("@personAccess.canAccess(authentication, #personId)")
                    .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer().jwt();
    }

    @Bean
    @ConditionalOnProperty(prefix = "account-service.security.jwt", name = "hmac-secret")
    public JwtDecoder hmacJwtDecoder(@Value("${account-service.security.jwt.hmac-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalStateException(
                    "account-service.security.jwt.hmac-secret must be at least " + MIN_HMAC_SECRET_BYTES + " bytes");
        }
        SecretKey secretKey = new SecretKeySpec(key, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtClaimValidator<List<String>> audience =
                new JwtClaimValidator<>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(AUDIENCE));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), audience));
        return decoder;
    }
}
