package ru.kondrashov.commonservice.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;

public class ServiceAuthInterceptor implements ClientHttpRequestInterceptor {

    private final String authToken;
    private final boolean requireTls;

    public ServiceAuthInterceptor(String authToken, boolean requireTls) {
        this.authToken = authToken;
        this.requireTls = requireTls;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI uri = request.getURI();
        if (requireTls && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new InsecureDownstreamUrlException(
                    "Refusing to call downstream service over " + uri.getScheme() + "://" + uri.getHost()
                            + "; https is required");
        }
        request.getHeaders().setBearerAuth(authToken);
        return execution.execute(request, body);
    }

    public static class InsecureDownstreamUrlException extends IllegalStateException {
        public InsecureDownstreamUrlException(String message) {
            super(message);
        }
    }
}
