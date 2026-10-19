package ru.kondrashov.accountservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceTlsEnforcerTest {

    private static MockEnvironment url(String url) {
        return new MockEnvironment().withProperty(DatasourceTlsEnforcer.URL_PROPERTY, url);
    }

    private static void assertAccepted(MockEnvironment environment) {
        assertThatCode(() -> DatasourceTlsEnforcer.verify(environment)).doesNotThrowAnyException();
    }

    private static void assertRejected(MockEnvironment environment) {
        assertThatThrownBy(() -> DatasourceTlsEnforcer.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not enforce TLS");
    }

    @Test
    void defaultApplicationUrlRequiresTls() {
        assertAccepted(url("jdbc:postgresql://localhost:5432/account-service-db?sslmode=require"));
        assertThat(DatasourceTlsEnforcer.effectiveSslMode(
                "jdbc:postgresql://localhost:5432/account-service-db?sslmode=require", new MockEnvironment()))
                .isEqualTo("require");
    }

    @Test
    void loopbackHostsMayConnectWithoutTls() {
        assertAccepted(url("jdbc:postgresql://localhost/account-service-db"));
        assertAccepted(url("jdbc:postgresql://127.0.0.1:5432/account-service-db?sslmode=disable"));
        assertAccepted(url("jdbc:postgresql://[::1]:5432/account-service-db"));
        assertAccepted(url("jdbc:postgresql:account-service-db"));
    }

    @Test
    void remoteHostWithoutSslParametersIsRejected() {
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db"));
        assertRejected(url("jdbc:postgresql://10.0.0.5:5432/account-service-db"));
    }

    @Test
    void remoteHostWithNonEnforcingSslModeIsRejected() {
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db?sslmode=disable"));
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db?sslmode=allow"));
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db?sslmode=prefer"));
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db?ssl=false"));
    }

    @Test
    void remoteHostWithEnforcingSslModeIsAccepted() {
        assertAccepted(url("jdbc:postgresql://db.internal/account-service-db?sslmode=require"));
        assertAccepted(url("jdbc:postgresql://db.internal/account-service-db?sslmode=verify-ca"));
        assertAccepted(url("jdbc:postgresql://db.internal:5432/account-service-db?sslmode=VERIFY-FULL&sslrootcert=/etc/ca.pem"));
        assertAccepted(url("jdbc:postgresql://db.internal/account-service-db?ssl=true"));
    }

    @Test
    void sslModeFromHikariDataSourcePropertiesIsHonoured() {
        assertAccepted(url("jdbc:postgresql://db.internal/account-service-db")
                .withProperty(DatasourceTlsEnforcer.DATA_SOURCE_PROPERTIES_PREFIX + "sslmode", "verify-full"));
        assertRejected(url("jdbc:postgresql://db.internal/account-service-db?sslmode=disable")
                .withProperty(DatasourceTlsEnforcer.DATA_SOURCE_PROPERTIES_PREFIX + "sslmode", "verify-full"));
    }

    @Test
    void anyRemoteHostInMultiHostUrlRequiresTls() {
        assertRejected(url("jdbc:postgresql://localhost:5432,db-replica.internal:5432/account-service-db"));
        assertThat(DatasourceTlsEnforcer.hosts("jdbc:postgresql://localhost:5432,[2001:db8::1]:5433/account-service-db"))
                .containsExactly("localhost", "[2001:db8::1]");
    }

    @Test
    void explicitOptOutAllowsInsecureRemoteConnection() {
        assertAccepted(url("jdbc:postgresql://db.internal/account-service-db")
                .withProperty(DatasourceTlsEnforcer.ALLOW_INSECURE_PROPERTY, "true"));
    }

    @Test
    void nonPostgresOrMissingUrlIsIgnored() {
        assertAccepted(new MockEnvironment());
        assertAccepted(url("jdbc:h2:mem:testdb"));
    }
}
