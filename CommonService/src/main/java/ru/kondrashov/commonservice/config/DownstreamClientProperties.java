package ru.kondrashov.commonservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "downstream")
public class DownstreamClientProperties {

    /** Reject any downstream URL that is not https. Only disable for local development. */
    private boolean requireTls = true;

    private Duration connectTimeout = Duration.ofSeconds(2);

    private Duration readTimeout = Duration.ofSeconds(10);

    /** Bearer token presented to AccountService and PersonService. */
    private String authToken;

    /** Optional trust store (e.g. internal CA) used to verify downstream certificates. */
    private String trustStore;
    private String trustStorePassword;
    private String trustStoreType = "PKCS12";

    /** Optional client key store, enabling mutual TLS with the downstream services. */
    private String keyStore;
    private String keyStorePassword;
    private String keyStoreType = "PKCS12";
}
