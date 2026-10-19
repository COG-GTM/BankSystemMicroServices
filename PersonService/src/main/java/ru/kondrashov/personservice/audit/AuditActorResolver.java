package ru.kondrashov.personservice.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Resolves who performed the current operation from the {@value #ACTOR_HEADER} request header
 * set by the calling service or gateway.
 */
@Component
public class AuditActorResolver {

    public static final String ACTOR_HEADER = "X-Actor-Id";
    public static final String ANONYMOUS = "anonymous";
    public static final String SYSTEM = "system";

    static final int MAX_ACTOR_LENGTH = 128;
    static final int MAX_ADDRESS_LENGTH = 64;

    public String currentActor() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return SYSTEM;
        }
        String actor = sanitize(request.getHeader(ACTOR_HEADER), MAX_ACTOR_LENGTH);
        return actor == null ? ANONYMOUS : actor;
    }

    public String currentClientAddress() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : sanitize(request.getRemoteAddr(), MAX_ADDRESS_LENGTH);
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }

    static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("[\\p{Cntrl}\\s]+", "_");
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
