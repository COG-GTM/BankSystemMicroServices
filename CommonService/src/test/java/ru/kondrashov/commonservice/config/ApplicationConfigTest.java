package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    private static final String[] HTTPS_URLS = {
            "people.URL=https://people.internal/v1/people",
            "person.URL=https://people.internal/v1/people/%s",
            "accounts.URL=https://accounts.internal/v1/people/%s/accounts",
            "account.URL=https://accounts.internal/v1/people/%s/accounts/%s",
            "bills.URL=https://accounts.internal/v1/people/%s/accounts/%s/bills",
            "bill.URL=https://accounts.internal/v1/people/%s/accounts/%s/bills/%s",
            "transactions.URL=https://accounts.internal/v1/people/%s/accounts/%s/bills/%s/transactions",
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class))
            .withUserConfiguration(ApplicationConfig.class)
            .withPropertyValues(HTTPS_URLS);

    @Test
    void buildsRestTemplateWithTimeoutsAndAuthInterceptor() {
        runner.withPropertyValues("downstream.auth-token=tok", "downstream.connect-timeout=3s",
                        "downstream.read-timeout=7s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    assertThat(restTemplate.getInterceptors())
                            .hasAtLeastOneElementOfType(ServiceAuthInterceptor.class);
                    Object factory = ReflectionTestUtils.getField(restTemplate, "requestFactory");
                    assertThat(factory).isInstanceOf(TlsClientHttpRequestFactory.class);
                    assertThat(ReflectionTestUtils.getField(factory, SimpleClientHttpRequestFactory.class,
                            "connectTimeout")).isEqualTo(3000);
                    assertThat(ReflectionTestUtils.getField(factory, SimpleClientHttpRequestFactory.class,
                            "readTimeout")).isEqualTo(7000);
                });
    }

    @Test
    void failsWithoutServiceToken() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().hasStackTraceContaining("SERVICE_AUTH_TOKEN"));
    }

    @Test
    void failsWhenDownstreamUrlIsPlainHttp() {
        runner.withPropertyValues("downstream.auth-token=tok", "person.URL=http://people.internal/v1/people/%s")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasStackTraceContaining("person.URL must use https"));
    }

    @Test
    void allowsPlainHttpWhenTlsRequirementDisabled() {
        runner.withPropertyValues("downstream.auth-token=tok", "downstream.require-tls=false",
                        "person.URL=http://localhost:8082/v1/people/%s")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsFastOnUnreadableTrustStore() {
        runner.withPropertyValues("downstream.auth-token=tok", "downstream.trust-store=file:/does/not/exist.p12")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void loadsTrustStoreFromPlainFilesystemPath(@TempDir Path dir) throws Exception {
        Path trustStore = dir.resolve("trust.p12");
        try (InputStream in = new ClassPathResource("downstream-truststore.p12").getInputStream()) {
            Files.copy(in, trustStore);
        }
        runner.withPropertyValues("downstream.auth-token=tok",
                        "downstream.trust-store=" + trustStore.toAbsolutePath(),
                        "downstream.trust-store-password=changeit")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void loadsTrustStoreFromPrefixedLocations(@TempDir Path dir) throws Exception {
        runner.withPropertyValues("downstream.auth-token=tok",
                        "downstream.trust-store=classpath:downstream-truststore.p12",
                        "downstream.trust-store-password=changeit")
                .run(context -> assertThat(context).hasNotFailed());

        Path trustStore = dir.resolve("trust.p12");
        try (InputStream in = new ClassPathResource("downstream-truststore.p12").getInputStream()) {
            Files.copy(in, trustStore);
        }
        runner.withPropertyValues("downstream.auth-token=tok",
                        "downstream.trust-store=" + trustStore.toUri(),
                        "downstream.trust-store-password=changeit")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
