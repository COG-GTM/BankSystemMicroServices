package ru.kondrashov.commonservice.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** Adds AccountService HTTP Basic credentials only to requests sent to AccountService's origin. */
public class AccountServiceBasicAuthInterceptor implements ClientHttpRequestInterceptor {

    private final URI accountServiceOrigin;
    private final BasicAuthenticationInterceptor delegate;

    public AccountServiceBasicAuthInterceptor(String accountServiceBaseUrl, String username, String password) {
        this.accountServiceOrigin = URI.create(accountServiceBaseUrl);
        this.delegate = new BasicAuthenticationInterceptor(username, password);
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (isAccountServiceRequest(request.getURI())) {
            return delegate.intercept(request, body, execution);
        }
        return execution.execute(request, body);
    }

    private boolean isAccountServiceRequest(URI uri) {
        return Objects.equals(uri.getScheme(), accountServiceOrigin.getScheme())
                && Objects.equals(uri.getHost(), accountServiceOrigin.getHost())
                && port(uri) == port(accountServiceOrigin);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
