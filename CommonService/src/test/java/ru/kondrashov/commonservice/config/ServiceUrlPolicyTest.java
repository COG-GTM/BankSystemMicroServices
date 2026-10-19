package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceUrlPolicyTest {

    private final DownstreamServicesProperties.Transport transport = new DownstreamServicesProperties.Transport();

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8082/v1/people/%s",
            "http://LOCALHOST:8081/v1/people/%s/accounts/%s",
            "http://127.0.0.1:8082/v1/people",
            "http://127.10.0.3:8082/v1/people",
            "http://[::1]:8081/v1/people/%s/accounts",
            "https://person-service.internal:8443/v1/people/%s",
            "https://10.0.0.5/v1/people/%s/accounts/%s/bills/%s/transactions"
    })
    void acceptsHttpsAndLoopbackHttp(String url) {
        assertThatCode(() -> ServiceUrlPolicy.check("person.URL", url, transport)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://person-service:8082/v1/people/%s",
            "http://10.0.0.5:8081/v1/people/%s/accounts",
            "http://localhost.evil.example/v1/people",
            "http://128.0.0.1:8082/v1/people"
    })
    void rejectsPlainHttpToNonLoopbackHosts(String url) {
        assertThatThrownBy(() -> ServiceUrlPolicy.check("person.URL", url, transport))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("person.URL")
                .hasMessageContaining("non-loopback");
    }

    @Test
    void allowsNonLoopbackHttpOnlyWithExplicitOptIn() {
        transport.setAllowInsecureNonLoopback(true);
        assertThatCode(() -> ServiceUrlPolicy.check("person.URL", "http://person-service:8082/v1/people", transport))
                .doesNotThrowAnyException();
    }

    @Test
    void requireHttpsRejectsLoopbackHttpToo() {
        transport.setRequireHttps(true);
        transport.setAllowInsecureNonLoopback(true);
        assertThatThrownBy(() -> ServiceUrlPolicy.check("people.URL", "http://localhost:8082/v1/people", transport))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("require-https");
        assertThatCode(() -> ServiceUrlPolicy.check("people.URL", "https://localhost:8443/v1/people", transport))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ftp://localhost/v1/people", "localhost:8082/v1/people", "http:///v1/people"})
    void rejectsMissingOrUnsupportedUrls(String url) {
        assertThatThrownBy(() -> ServiceUrlPolicy.check("people.URL", url, transport))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void loopbackDetectionDoesNotTrustHostnamePrefixes() {
        assertThat(ServiceUrlPolicy.isLoopback("localhost")).isTrue();
        assertThat(ServiceUrlPolicy.isLoopback("127.0.0.1")).isTrue();
        assertThat(ServiceUrlPolicy.isLoopback("[::1]")).isTrue();
        assertThat(ServiceUrlPolicy.isLoopback("127.0.0.1.nip.io")).isFalse();
        assertThat(ServiceUrlPolicy.isLoopback("localhost.example.com")).isFalse();
        assertThat(ServiceUrlPolicy.isLoopback(null)).isFalse();
    }

    @Test
    void localhostCountsAsLoopbackOnlyWhenEveryResolvedAddressIsLoopback() throws Exception {
        InetAddress loopback = InetAddress.getByAddress("localhost", new byte[]{127, 0, 0, 1});
        InetAddress remote = InetAddress.getByAddress("localhost", new byte[]{10, 0, 0, 7});

        assertThat(ServiceUrlPolicy.isLoopback("localhost", h -> new InetAddress[]{loopback})).isTrue();
        assertThat(ServiceUrlPolicy.isLoopback("localhost", h -> new InetAddress[]{remote})).isFalse();
        assertThat(ServiceUrlPolicy.isLoopback("localhost", h -> new InetAddress[]{loopback, remote})).isFalse();
        assertThat(ServiceUrlPolicy.isLoopback("localhost", h -> {
            throw new UnknownHostException(h);
        })).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://gateway.example/team%20a/v1/people/%s",
            "https://gateway.example/a%2Fb/v1/people",
            "http://localhost:8082/v1/people/%d"
    })
    void rejectsPercentSequencesOtherThanPlaceholders(String url) {
        assertThatThrownBy(() -> ServiceUrlPolicy.check("person.URL", url, transport))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("%s placeholders");
    }
}
