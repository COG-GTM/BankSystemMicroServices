package ru.kondrashov.accountservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersonAccessPolicyTest {

    private final PersonAccessPolicy policy = new PersonAccessPolicy();

    private static UsernamePasswordAuthenticationToken authenticated(AccountServicePrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void adminMayAccessAnyPerson() {
        AccountServicePrincipal admin = new AccountServicePrincipal("admin", "x", ClientRole.ADMIN, null);
        assertThat(policy.canAccessPerson(authenticated(admin), UUID.randomUUID().toString())).isTrue();
    }

    @Test
    void customerMayOnlyAccessOwnPerson() {
        UUID personId = UUID.randomUUID();
        AccountServicePrincipal customer = new AccountServicePrincipal("customer", "x", ClientRole.CUSTOMER, personId);
        assertThat(policy.canAccessPerson(authenticated(customer), personId.toString())).isTrue();
        assertThat(policy.canAccessPerson(authenticated(customer), UUID.randomUUID().toString())).isFalse();
        assertThat(policy.canAccessPerson(authenticated(customer), "not-a-uuid")).isFalse();
    }

    @Test
    void unauthenticatedOrUnknownPrincipalIsDenied() {
        assertThat(policy.canAccessPerson(null, UUID.randomUUID().toString())).isFalse();
        TestingAuthenticationToken unauthenticated = new TestingAuthenticationToken("anon", null, "ROLE_ADMIN");
        unauthenticated.setAuthenticated(false);
        assertThat(policy.canAccessPerson(unauthenticated, UUID.randomUUID().toString())).isFalse();
        TestingAuthenticationToken customerWithoutPrincipal = new TestingAuthenticationToken("c", null, "ROLE_CUSTOMER");
        assertThat(policy.canAccessPerson(customerWithoutPrincipal, UUID.randomUUID().toString())).isFalse();
    }
}
