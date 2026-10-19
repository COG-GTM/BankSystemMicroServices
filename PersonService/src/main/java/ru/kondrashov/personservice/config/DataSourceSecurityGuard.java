package ru.kondrashov.personservice.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Refuses to start the service when the datasource is configured insecurely: credentials must be supplied
 * by the environment, a non-local PostgreSQL host must be reached over TLS, and the application must not
 * log in as a superuser role.
 * Runs as soon as the DataSource bean is created, before Hibernate touches the schema.
 */
@Component
public class DataSourceSecurityGuard implements BeanPostProcessor, EnvironmentAware {

    static final String POSTGRES_URL_PREFIX = "jdbc:postgresql:";
    static final Set<String> SECURE_SSL_MODES = new HashSet<>(Arrays.asList("require", "verify-ca", "verify-full"));
    private static final Set<String> LOCAL_HOSTS = new HashSet<>(Arrays.asList("localhost", "127.0.0.1", "[::1]"));
    private static final String SUPERUSER_QUERY = "SELECT rolsuper FROM pg_roles WHERE rolname = current_user";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource) {
            requireCredentials(environment);
            requireSecureTransport(environment.getProperty("spring.datasource.url"));
            requireNonSuperuser((DataSource) bean);
        }
        return bean;
    }

    static void requireCredentials(Environment environment) {
        requireProperty(environment, "spring.datasource.username", "DB_USER");
        requireProperty(environment, "spring.datasource.password", "DB_PASSWORD");
    }

    private static void requireProperty(Environment environment, String property, String variable) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            value = null;
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(variable + " is not set: supply the database credentials from the environment");
        }
    }

    static void requireSecureTransport(String url) {
        if (url == null || !url.startsWith(POSTGRES_URL_PREFIX) || isLocal(url)) {
            return;
        }
        String sslMode = queryParameter(url, "sslmode");
        if (sslMode == null || !SECURE_SSL_MODES.contains(sslMode.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Refusing to connect to a non-local database without TLS: "
                    + "add sslmode=require (or verify-ca / verify-full) to DB_URL");
        }
    }

    static void requireNonSuperuser(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(SUPERUSER_QUERY)) {
                if (resultSet.next() && resultSet.getBoolean(1)) {
                    throw new IllegalStateException("Refusing to run as a PostgreSQL superuser: "
                            + "set DB_USER to a least-privilege application role (see db/create-app-roles.sql)");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify the privileges of the database role", e);
        }
    }

    static boolean isLocal(String url) {
        String rest = url.substring(POSTGRES_URL_PREFIX.length());
        if (!rest.startsWith("//")) {
            return true;
        }
        String authority = rest.substring(2).split("[/?]", 2)[0];
        if (authority.isEmpty()) {
            return true;
        }
        for (String hostAndPort : authority.split(",")) {
            if (!LOCAL_HOSTS.contains(host(hostAndPort).toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private static String host(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int end = hostAndPort.indexOf(']');
            return end < 0 ? hostAndPort : hostAndPort.substring(0, end + 1);
        }
        int colon = hostAndPort.indexOf(':');
        return colon < 0 ? hostAndPort : hostAndPort.substring(0, colon);
    }

    private static String queryParameter(String url, String name) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        String value = null;
        for (String pair : url.substring(queryStart + 1).split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equalsIgnoreCase(name)) {
                value = keyValue[1];
            }
        }
        return value;
    }
}
