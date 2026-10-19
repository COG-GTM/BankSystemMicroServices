package ru.kondrashov.commonservice.config;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises mutual TLS against a local HTTPS server that requires a client certificate.
 */
class TlsClientHttpRequestFactoryTest {

    private static final String PASSWORD = "changeit";

    @TempDir
    static Path dir;

    private static HttpsServer server;
    private static String url;

    @BeforeAll
    static void startMutualTlsServer() throws Exception {
        generateKeyPair("server");
        generateKeyPair("client");
        trust("server", "client-trust.p12");
        trust("client", "server-trust.p12");

        SSLContext serverContext = TlsClientHttpRequestFactory.buildSslContext(tls("server.p12", "server-trust.p12"));
        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = getSSLContext().getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                params.setSSLParameters(sslParameters);
            }
        });
        server.createContext("/v1/people", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        url = "https://localhost:" + server.getAddress().getPort() + "/v1/people";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void presentsClientCertificateAndTrustsConfiguredCa() {
        RestTemplate restTemplate = restTemplate(tls("client.p12", "client-trust.p12"));

        assertThat(restTemplate.getForObject(url, String.class)).isEqualTo("[]");
    }

    @Test
    void failsWithoutClientCertificate() {
        RestTemplate restTemplate = restTemplate(tls(null, "client-trust.p12"));

        assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void failsWhenServerCertificateIsNotTrusted() {
        RestTemplate restTemplate = restTemplate(tls("client.p12", null));

        assertThatThrownBy(() -> restTemplate.getForObject(url, String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void reportsUnreadableKeyStore() {
        DownstreamServicesProperties.Tls tls = tls("client.p12", "client-trust.p12");
        tls.setKeyStorePassword("wrong");

        assertThatThrownBy(() -> TlsClientHttpRequestFactory.buildSslContext(tls))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("downstream services");
    }

    @Test
    void doesNotFollowRedirects() throws Exception {
        AtomicInteger redirectTargetHits = new AtomicInteger();
        HttpServer plain = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        plain.createContext("/v1/people", exchange -> {
            exchange.getResponseHeaders().add("Location", "/elsewhere");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        plain.createContext("/elsewhere", exchange -> {
            redirectTargetHits.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        plain.start();
        try {
            RestTemplate restTemplate = new RestTemplate(new TlsClientHttpRequestFactory());
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "http://127.0.0.1:" + plain.getAddress().getPort() + "/v1/people", String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirectTargetHits.get()).isZero();
        } finally {
            plain.stop(0);
        }
    }

    private static RestTemplate restTemplate(DownstreamServicesProperties.Tls tls) {
        return new RestTemplate(new TlsClientHttpRequestFactory(TlsClientHttpRequestFactory.buildSslContext(tls)));
    }

    private static DownstreamServicesProperties.Tls tls(String keyStore, String trustStore) {
        DownstreamServicesProperties.Tls tls = new DownstreamServicesProperties.Tls();
        if (keyStore != null) {
            tls.setKeyStore(fileUrl(keyStore));
            tls.setKeyStorePassword(PASSWORD);
        }
        if (trustStore != null) {
            tls.setTrustStore(fileUrl(trustStore));
            tls.setTrustStorePassword(PASSWORD);
        }
        return tls;
    }

    private static String fileUrl(String name) {
        return dir.resolve(name).toUri().toString();
    }

    private static void generateKeyPair(String alias) throws Exception {
        keytool("-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", path(alias + ".p12"),
                "-storepass", PASSWORD, "-keypass", PASSWORD);
        keytool("-exportcert", "-alias", alias, "-keystore", path(alias + ".p12"),
                "-storepass", PASSWORD, "-file", path(alias + ".cer"));
    }

    private static void trust(String alias, String trustStore) throws Exception {
        keytool("-importcert", "-noprompt", "-alias", alias, "-file", path(alias + ".cer"),
                "-storetype", "PKCS12", "-keystore", path(trustStore), "-storepass", PASSWORD);
    }

    private static String path(String name) {
        return dir.resolve(name).toString();
    }

    private static void keytool(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(dir.resolve("keytool.log").toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + command);
        }
    }
}
