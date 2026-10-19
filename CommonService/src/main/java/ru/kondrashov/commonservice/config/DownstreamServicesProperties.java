package ru.kondrashov.commonservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Data
@ConfigurationProperties(prefix = "services")
public class DownstreamServicesProperties {

    private final Transport transport = new Transport();
    private final Tls tls = new Tls();

    @Data
    public static class Transport {
        /** Reject plain http:// even for loopback hosts. */
        private boolean requireHttps;
        /** Escape hatch for plain http:// to non-loopback hosts, e.g. when a service mesh adds TLS. */
        private boolean allowInsecureNonLoopback;
    }

    @Data
    public static class Tls {
        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType = "PKCS12";
        private String trustStore;
        private String trustStorePassword;
        private String trustStoreType = "PKCS12";

        public boolean isConfigured() {
            return StringUtils.hasText(keyStore) || StringUtils.hasText(trustStore);
        }
    }
}
