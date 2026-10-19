package ru.kondrashov.commonservice.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.HttpURLConnection;

public class TlsClientHttpRequestFactory extends SimpleClientHttpRequestFactory {

    private final SSLSocketFactory sslSocketFactory;

    public TlsClientHttpRequestFactory(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    @Override
    protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
        }
        super.prepareConnection(connection, httpMethod);
    }
}
