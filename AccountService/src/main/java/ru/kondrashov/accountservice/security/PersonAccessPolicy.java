package ru.kondrashov.accountservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

/** Decides whether the authenticated caller may act on the person identified in the request path. */
public class PersonAccessPolicy {

    public boolean canAccessPerson(Authentication authentication, String personId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (hasAuthority(authentication, ClientRole.ADMIN.authority())) {
            return true;
        }
        if (!hasAuthority(authentication, ClientRole.CUSTOMER.authority())
                || !(authentication.getPrincipal() instanceof AccountServicePrincipal)) {
            return false;
        }
        UUID callerPersonId = ((AccountServicePrincipal) authentication.getPrincipal()).getPersonId();
        UUID requestedPersonId = parse(personId);
        return callerPersonId != null && callerPersonId.equals(requestedPersonId);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private static UUID parse(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
