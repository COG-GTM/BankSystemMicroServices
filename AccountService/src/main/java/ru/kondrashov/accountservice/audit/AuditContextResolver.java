package ru.kondrashov.accountservice.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reads audit metadata from the current HTTP request. Until the service has authentication,
 * the acting user is taken from the {@value #ACTOR_HEADER} header.
 */
@Component
public class AuditContextResolver {

    public static final String ACTOR_HEADER = "X-Actor-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String IDENTIFIER_REGEXP = "[A-Za-z0-9._@:\\-]{1,128}";

    private static final Pattern IDENTIFIER = Pattern.compile(IDENTIFIER_REGEXP);

    public static boolean isValidIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    public Optional<String> actor() {
        return header(ACTOR_HEADER).filter(AuditContextResolver::isValidIdentifier);
    }

    public String requestId() {
        return header(REQUEST_ID_HEADER)
                .filter(AuditContextResolver::isValidIdentifier)
                .orElseGet(() -> UUID.randomUUID().toString());
    }

    public String clientAddress() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).orElse(null);
    }

    private Optional<String> header(String name) {
        return currentRequest().map(request -> request.getHeader(name));
    }

    private Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return Optional.of(((ServletRequestAttributes) attributes).getRequest());
        }
        return Optional.empty();
    }
}
