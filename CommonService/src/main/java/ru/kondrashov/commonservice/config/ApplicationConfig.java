package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(DownstreamClientProperties.class)
public class ApplicationConfig {

    static final List<String> DOWNSTREAM_URL_PROPERTIES = Arrays.asList(
            "people.URL", "person.URL", "accounts.URL", "account.URL",
            "bills.URL", "bill.URL", "transactions.URL");

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder,
                                     DownstreamClientProperties properties,
                                     Environment environment,
                                     ResourceLoader resourceLoader) throws GeneralSecurityException, IOException {
        if (!StringUtils.hasText(properties.getAuthToken())) {
            throw new IllegalStateException(
                    "downstream.auth-token (env SERVICE_AUTH_TOKEN) must be set to call AccountService/PersonService");
        }
        if (properties.isRequireTls()) {
            verifyDownstreamUrlsUseHttps(environment);
        }

        TlsClientHttpRequestFactory requestFactory =
                new TlsClientHttpRequestFactory(sslContext(properties, resourceLoader).getSocketFactory());
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        return restTemplateBuilder
                .requestFactory(() -> requestFactory)
                .additionalInterceptors(
                        new ServiceAuthInterceptor(properties.getAuthToken(), properties.isRequireTls()))
                .build();
    }

    static void verifyDownstreamUrlsUseHttps(Environment environment) {
        for (String property : DOWNSTREAM_URL_PROPERTIES) {
            String url = environment.getRequiredProperty(property);
            if (!"https".equalsIgnoreCase(URI.create(url.replace("%s", "x")).getScheme())) {
                throw new IllegalStateException(property + " must use https when downstream.require-tls is true");
            }
        }
    }

    static SSLContext sslContext(DownstreamClientProperties properties, ResourceLoader resourceLoader)
            throws GeneralSecurityException, IOException {
        KeyManager[] keyManagers = null;
        if (StringUtils.hasText(properties.getKeyStore())) {
            KeyStore keyStore = loadKeyStore(resourceLoader, properties.getKeyStore(),
                    properties.getKeyStoreType(), properties.getKeyStorePassword());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, toChars(properties.getKeyStorePassword()));
            keyManagers = kmf.getKeyManagers();
        }

        TrustManager[] trustManagers = null;
        if (StringUtils.hasText(properties.getTrustStore())) {
            KeyStore trustStore = loadKeyStore(resourceLoader, properties.getTrustStore(),
                    properties.getTrustStoreType(), properties.getTrustStorePassword());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);
        return sslContext;
    }

    private static KeyStore loadKeyStore(ResourceLoader resourceLoader, String location, String type, String password)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream in = resolveStore(resourceLoader, location).getInputStream()) {
            keyStore.load(in, toChars(password));
        }
        return keyStore;
    }

    static Resource resolveStore(ResourceLoader resourceLoader, String location) {
        return ResourceUtils.isUrl(location) ? resourceLoader.getResource(location) : new FileSystemResource(location);
    }

    private static char[] toChars(String password) {
        return password == null ? null : password.toCharArray();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}
