package ru.kondrashov.commonservice.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adds PersonService HTTP Basic credentials, but only to requests sent to a configured PersonService origin,
 * so they are never leaked to other downstream services that share the RestTemplate.
 */
public class PersonServiceAuthInterceptor implements ClientHttpRequestInterceptor {

    static final int MIN_PASSWORD_LENGTH = 16;

    private final List<URI> personServiceOrigins;
    private final String username;
    private final String password;

    public PersonServiceAuthInterceptor(List<String> personServiceUrls, String username, String password) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalStateException("person-service.client.username must be set");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("person-service.client.password must be set and at least "
                    + MIN_PASSWORD_LENGTH + " characters long");
        }
        this.personServiceOrigins = personServiceUrls.stream().map(URI::create).collect(Collectors.toList());
        this.username = username;
        this.password = password;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (isPersonService(request.getURI())) {
            request.getHeaders().setBasicAuth(username, password);
        }
        return execution.execute(request, body);
    }

    boolean isPersonService(URI uri) {
        return personServiceOrigins.stream().anyMatch(origin -> sameOrigin(origin, uri));
    }

    private static boolean sameOrigin(URI origin, URI uri) {
        return origin.getScheme().equalsIgnoreCase(Objects.toString(uri.getScheme(), ""))
                && origin.getHost().equalsIgnoreCase(Objects.toString(uri.getHost(), ""))
                && port(origin) == port(uri);
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
