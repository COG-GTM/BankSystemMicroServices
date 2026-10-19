package ru.kondrashov.commonservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiUsersPropertiesTest {

    private static final String BCRYPT = "{bcrypt}$2a$10$I0cfO/nWXAio2tHq/k4j5ejeajLsEzM4F1JEh6p1O2H8blTh.Fi3e";

    private static UiUsersProperties.UiUser user(String username, String password, String... roles) {
        UiUsersProperties.UiUser user = new UiUsersProperties.UiUser();
        user.setUsername(username);
        user.setPassword(password);
        user.setRoles(Arrays.asList(roles));
        return user;
    }

    @Test
    void validUserMapsToRoleAuthorities() {
        UserDetails details = user("alice", BCRYPT, "STAFF", "ADMIN").toUserDetails();
        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_STAFF", "ROLE_ADMIN");
    }

    @Test
    void plainTextPasswordIsRejected() {
        assertThatThrownBy(() -> user("alice", "secret", "STAFF").toUserDetails())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-encoded");
    }

    @Test
    void noopPasswordIsRejected() {
        assertThatThrownBy(() -> user("alice", "{noop}secret", "STAFF").toUserDetails())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankUsernameIsRejected() {
        assertThatThrownBy(() -> user(" ", BCRYPT, "STAFF").toUserDetails())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrUnknownRoleIsRejected() {
        assertThatThrownBy(() -> user("alice", BCRYPT).toUserDetails())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user("alice", BCRYPT, "ROOT").toUserDetails())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported role");
    }

    @Test
    void noConfiguredUsersFailsFast() {
        UiUsersProperties properties = new UiUsersProperties();
        properties.setUsers(Collections.emptyList());
        assertThatThrownBy(() -> new SecurityConfig().userDetailsService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No web UI users configured");
    }
}
