package ru.kondrashov.accountservice.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.yaml.snakeyaml.Yaml;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceSecurityGuardTest {

    @Test
    void rejectsMissingCredentials() {
        MockEnvironment unresolved = new MockEnvironment()
                .withProperty("spring.datasource.username", "${DB_USER}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        MockEnvironment blankPassword = new MockEnvironment()
                .withProperty("spring.datasource.username", "app")
                .withProperty("spring.datasource.password", "");

        assertEquals("DB_USER is not set: supply the database credentials from the environment",
                assertThrows(IllegalStateException.class,
                        () -> DataSourceSecurityGuard.requireCredentials(unresolved)).getMessage());
        assertEquals("DB_PASSWORD is not set: supply the database credentials from the environment",
                assertThrows(IllegalStateException.class,
                        () -> DataSourceSecurityGuard.requireCredentials(blankPassword)).getMessage());
    }

    @Test
    void acceptsCredentialsFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_USER", "app")
                .withProperty("DB_PASSWORD", "from-secret-store")
                .withProperty("spring.datasource.username", "${DB_USER}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertDoesNotThrow(() -> DataSourceSecurityGuard.requireCredentials(environment));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost/account-service-db",
            "jdbc:postgresql://localhost:5432/account-service-db",
            "jdbc:postgresql://127.0.0.1:5432/account-service-db",
            "jdbc:postgresql://[::1]:5432/account-service-db",
            "jdbc:postgresql:account-service-db",
            "jdbc:postgresql:///account-service-db",
            "jdbc:postgresql://db.internal:5432/account-service-db?sslmode=require",
            "jdbc:postgresql://db.internal/account-service-db?ApplicationName=accounts&sslmode=verify-full",
            "jdbc:postgresql://db1.internal,db2.internal/account-service-db?sslmode=VERIFY-CA"
    })
    void allowsLocalOrTlsUrls(String url) {
        assertDoesNotThrow(() -> DataSourceSecurityGuard.requireSecureTransport(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://db.internal/account-service-db",
            "jdbc:postgresql://db.internal:5432/account-service-db?sslmode=prefer",
            "jdbc:postgresql://db.internal/account-service-db?sslmode=disable",
            "jdbc:postgresql://db.internal/account-service-db?sslmode=require&sslmode=allow",
            "jdbc:postgresql://localhost.attacker.example/account-service-db",
            "jdbc:postgresql://localhost,db.internal/account-service-db"
    })
    void rejectsNonLocalUrlsWithoutTls(String url) {
        assertThrows(IllegalStateException.class, () -> DataSourceSecurityGuard.requireSecureTransport(url));
    }

    @Test
    void rejectsSuperuserRole() throws SQLException {
        DataSource dataSource = postgresDataSource(true);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataSourceSecurityGuard.requireSafeSession(dataSource, false));
        assertEquals("Refusing to run as a PostgreSQL superuser: set DB_USER to a least-privilege application role "
                + "(see db/create-app-roles.sql)", e.getMessage());
    }

    @Test
    void allowsNonSuperuserRole() throws SQLException {
        assertDoesNotThrow(() -> DataSourceSecurityGuard.requireSafeSession(postgresDataSource(false), false));
    }

    @Test
    void rejectsUnencryptedSessionToRemoteDatabase() throws SQLException {
        DataSource dataSource = postgresDataSource(false, false);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DataSourceSecurityGuard.requireSafeSession(dataSource, true));
        assertEquals("Refusing to use an unencrypted connection to a non-local database: "
                + "the session was opened without TLS", e.getMessage());
    }

    @Test
    void allowsEncryptedSessionToRemoteDatabase() throws SQLException {
        assertDoesNotThrow(() -> DataSourceSecurityGuard.requireSafeSession(postgresDataSource(false, true), true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://localhost:5432/db",
            "jdbc:postgresql:db",
    })
    void localUrlsDoNotRequireTls(String url) {
        assertEquals(false, DataSourceSecurityGuard.requiresTls(url));
    }

    @Test
    void remoteUrlRequiresTls() {
        assertEquals(true, DataSourceSecurityGuard.requiresTls("jdbc:postgresql://db.internal.example:5432/db?sslmode=require"));
    }

    @Test
    void skipsRoleCheckForOtherDatabases() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("H2");
        when(connection.getMetaData()).thenReturn(metaData);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);

        assertDoesNotThrow(() -> DataSourceSecurityGuard.requireSafeSession(dataSource, true));
        verify(connection, never()).createStatement();
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationConfigTakesCredentialsFromEnvironmentOnly() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            Map<String, Object> config = new Yaml().load(in);
            Map<String, Object> datasource = (Map<String, Object>) ((Map<String, Object>) config.get("spring")).get("datasource");
            assertEquals("${DB_USER}", datasource.get("username"));
            assertEquals("${DB_PASSWORD}", datasource.get("password"));
        }
    }

    private static DataSource postgresDataSource(boolean superuser) throws SQLException {
        return postgresDataSource(superuser, true);
    }

    private static DataSource postgresDataSource(boolean superuser, boolean ssl) throws SQLException {
        Statement statement = mock(Statement.class);
        ResultSet roleResult = flagResult(superuser);
        ResultSet tlsResult = flagResult(ssl);
        when(statement.executeQuery(contains("pg_roles"))).thenReturn(roleResult);
        when(statement.executeQuery(contains("pg_stat_ssl"))).thenReturn(tlsResult);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    private static ResultSet flagResult(boolean value) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean(1)).thenReturn(value);
        return resultSet;
    }
}
