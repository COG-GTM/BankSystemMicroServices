package ru.kondrashov.accountservice.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Decides whether the authenticated caller may act on the resources of a person.
 * Trusted services carry the {@code accounts:admin} scope; end-user tokens carry a
 * {@code person_id} claim and may only access that person's resources.
 */
@Component("personAccess")
public class PersonAccess {

    public static final String ADMIN_AUTHORITY = "SCOPE_accounts:admin";
    public static final String PERSON_ID_CLAIM = "person_id";

    public boolean canAccess(Authentication authentication, Object personId) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return false;
        }
        UUID target = toUuid(personId);
        if (target == null) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        if (admin) {
            return true;
        }
        if (!(authentication.getPrincipal() instanceof Jwt)) {
            return false;
        }
        UUID caller = toUuid(((Jwt) authentication.getPrincipal()).getClaimAsString(PERSON_ID_CLAIM));
        return target.equals(caller);
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID) {
            return (UUID) value;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
