package ru.kondrashov.personservice.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Resolves who is performing the current change. The service has no authentication yet,
 * so the actor is taken from the {@value #ACTOR_HEADER} request header supplied by the caller;
 * replace with the authenticated principal once security is added.
 */
@Component("auditActorProvider")
public class AuditActorProvider implements AuditorAware<String> {

    public static final String ACTOR_HEADER = "X-Actor-Id";
    static final String ANONYMOUS = "anonymous";
    static final String SYSTEM = "system";
    private static final int MAX_LENGTH = 100;

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(currentActor());
    }

    public String currentActor() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return SYSTEM;
        }
        String actor = sanitize(request.getHeader(ACTOR_HEADER));
        return actor.isEmpty() ? ANONYMOUS : actor;
    }

    public String currentSourceAddress() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : sanitize(request.getRemoteAddr());
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }

    static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }
}
