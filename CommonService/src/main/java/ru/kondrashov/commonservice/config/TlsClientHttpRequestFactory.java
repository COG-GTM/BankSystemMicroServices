package ru.kondrashov.commonservice.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * Request factory for downstream calls. Never follows redirects, so a downstream response cannot steer a request
 * to a URL that bypassed {@link ServiceUrlPolicy}. When given an {@link SSLContext} it also presents a client
 * certificate and trusts a dedicated CA (mutual TLS). Hostname verification is left at the JDK default.
 */
public class TlsClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    private final SSLSocketFactory socketFactory;

    public TlsClientHttpRequestFactory() {
        this(null);
    }

    public TlsClientHttpRequestFactory(SSLContext sslContext) {
        this.socketFactory = sslContext == null ? null : sslContext.getSocketFactory();
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (socketFactory != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
        }
        super.prepareConnection(connection, httpMethod);
        connection.setInstanceFollowRedirects(false);
    }

    public static SSLContext buildSslContext(DownstreamServicesProperties.Tls tls) {
        try {
            KeyManagerFactory kmf = null;
            if (StringUtils.hasText(tls.getKeyStore())) {
                char[] password = toChars(tls.getKeyStorePassword());
                KeyStore keyStore = load(tls.getKeyStore(), tls.getKeyStoreType(), password);
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, password);
            }
            TrustManagerFactory tmf = null;
            if (StringUtils.hasText(tls.getTrustStore())) {
                KeyStore trustStore = load(tls.getTrustStore(), tls.getTrustStoreType(), toChars(tls.getTrustStorePassword()));
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf == null ? null : kmf.getKeyManagers(), tmf == null ? null : tmf.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Could not initialise TLS for downstream services", e);
        }
    }

    private static KeyStore load(String location, String type, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(StringUtils.hasText(type) ? type : "PKCS12");
        try (InputStream in = ResourceUtils.getURL(location).openStream()) {
            store.load(in, password);
        }
        return store;
    }

    private static char[] toChars(String value) {
        return value == null ? null : value.toCharArray();
    }
}
