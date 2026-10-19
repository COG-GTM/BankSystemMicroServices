package ru.kondrashov.accountservice.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

@Component
public class AuditActorResolver {

    public static final String ACTOR_HEADER = "X-Actor-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String ANONYMOUS = "anonymous";
    static final String SYSTEM = "system";
    static final int MAX_VALUE_LENGTH = 128;

    public AuditActor resolve() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return new AuditActor(SYSTEM, ActorSource.SYSTEM, null, null);
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        String clientAddress = sanitize(request.getRemoteAddr());
        String requestId = sanitize(request.getHeader(REQUEST_ID_HEADER));

        Principal principal = request.getUserPrincipal();
        String principalName = principal == null ? null : sanitize(principal.getName());
        if (principalName != null) {
            return new AuditActor(principalName, ActorSource.PRINCIPAL, clientAddress, requestId);
        }
        String asserted = sanitize(request.getHeader(ACTOR_HEADER));
        if (asserted != null) {
            return new AuditActor(asserted, ActorSource.HEADER, clientAddress, requestId);
        }
        return new AuditActor(ANONYMOUS, ActorSource.NONE, clientAddress, requestId);
    }

    static String sanitize(String value) {
        return sanitize(value, MAX_VALUE_LENGTH);
    }

    static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[^\\x20-\\x7E]", "_").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }
}
