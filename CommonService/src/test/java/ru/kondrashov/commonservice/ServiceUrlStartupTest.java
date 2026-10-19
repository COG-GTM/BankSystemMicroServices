package ru.kondrashov.commonservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceUrlStartupTest {

    @Test
    void refusesToStartWithPlainHttpToRemotePersonService() {
        assertThatThrownBy(() -> run("--services.person.base-url=http://person-service:8082"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("people.URL points at non-loopback host 'person-service'");
    }

    @Test
    void refusesToStartWithPlainHttpToRemoteAccountService() {
        assertThatThrownBy(() -> run("--services.account.base-url=http://10.0.0.7:8081"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("accounts.URL points at non-loopback host '10.0.0.7'");
    }

    @Test
    void startsWithHttpsRemoteServices() {
        try (ConfigurableApplicationContext context = run(
                "--services.person.base-url=https://person-service:8443",
                "--services.account.base-url=https://account-service:8443")) {
            assertThat(context.getEnvironment().getProperty("transactions.URL"))
                    .isEqualTo("https://account-service:8443/v1/people/%s/accounts/%s/bills/%s/transactions");
        }
    }

    @Test
    void startsWithRemotePlainHttpOnlyWhenExplicitlyAllowed() {
        try (ConfigurableApplicationContext context = run(
                "--services.person.base-url=http://person-service:8082",
                "--services.transport.allow-insecure-non-loopback=true")) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private static ConfigurableApplicationContext run(String... args) {
        return new SpringApplicationBuilder(CommonServiceApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
