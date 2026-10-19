package ru.kondrashov.accountservice.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Refuses to start when the PostgreSQL datasource points at a non-loopback host
 * without a TLS-enforcing sslmode, so personal data and DB credentials never
 * cross the network in clear text.
 */
public class DatasourceTlsEnforcer implements EnvironmentPostProcessor, Ordered {

    static final String URL_PROPERTY = "spring.datasource.url";
    static final String DATA_SOURCE_PROPERTIES_PREFIX = "spring.datasource.hikari.data-source-properties.";
    static final String ALLOW_INSECURE_PROPERTY = "app.datasource.allow-insecure-transport";

    private static final String JDBC_PREFIX = "jdbc:postgresql:";
    private static final Set<String> TLS_ENFORCING_MODES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("require", "verify-ca", "verify-full")));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        verify(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void verify(Environment environment) {
        String url = environment.getProperty(URL_PROPERTY);
        if (url == null || !url.startsWith(JDBC_PREFIX)) {
            return;
        }
        if (environment.getProperty(ALLOW_INSECURE_PROPERTY, Boolean.class, false)) {
            return;
        }
        List<String> remoteHosts = new ArrayList<>();
        for (String host : hosts(url)) {
            if (!isLoopback(host)) {
                remoteHosts.add(host);
            }
        }
        if (remoteHosts.isEmpty()) {
            return;
        }
        String sslMode = effectiveSslMode(url, environment);
        if (!TLS_ENFORCING_MODES.contains(sslMode)) {
            throw new IllegalStateException(String.format(
                    "%s points at non-loopback host(s) %s with sslmode=%s, which does not enforce TLS. "
                            + "Use sslmode=verify-full (or at least require), or set %s=true "
                            + "if transport encryption is provided outside the JDBC driver.",
                    URL_PROPERTY, remoteHosts, sslMode, ALLOW_INSECURE_PROPERTY));
        }
    }

    static List<String> hosts(String url) {
        String rest = url.substring(JDBC_PREFIX.length());
        if (!rest.startsWith("//")) {
            return Collections.singletonList("localhost");
        }
        rest = rest.substring(2);
        int end = rest.length();
        for (char delimiter : new char[]{'/', '?'}) {
            int index = rest.indexOf(delimiter);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String authority = rest.substring(0, end);
        if (authority.isEmpty()) {
            return Collections.singletonList("localhost");
        }
        List<String> hosts = new ArrayList<>();
        for (String hostAndPort : authority.split(",")) {
            hosts.add(stripPort(hostAndPort.trim()));
        }
        return hosts;
    }

    static String effectiveSslMode(String url, Environment environment) {
        Map<String, String> params = queryParameters(url);
        String sslMode = firstNonNull(params.get("sslmode"),
                environment.getProperty(DATA_SOURCE_PROPERTIES_PREFIX + "sslmode"));
        if (sslMode != null) {
            return sslMode.trim().toLowerCase(Locale.ROOT);
        }
        String ssl = firstNonNull(params.get("ssl"), environment.getProperty(DATA_SOURCE_PROPERTIES_PREFIX + "ssl"));
        if ("true".equalsIgnoreCase(ssl == null ? null : ssl.trim())) {
            return "verify-full";
        }
        return "prefer";
    }

    static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.isEmpty()
                || normalized.equals("localhost")
                || normalized.equals("::1")
                || normalized.equals("0:0:0:0:0:0:0:1")
                || normalized.matches("127(\\.\\d{1,3}){3}");
    }

    private static String stripPort(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int close = hostAndPort.indexOf(']');
            return close >= 0 ? hostAndPort.substring(0, close + 1) : hostAndPort;
        }
        int colon = hostAndPort.indexOf(':');
        return colon >= 0 ? hostAndPort.substring(0, colon) : hostAndPort;
    }

    private static Map<String, String> queryParameters(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        int query = url.indexOf('?');
        if (query < 0) {
            return params;
        }
        for (String pair : url.substring(query + 1).split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT);
            params.put(key, eq >= 0 ? pair.substring(eq + 1) : "");
        }
        return params;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}
