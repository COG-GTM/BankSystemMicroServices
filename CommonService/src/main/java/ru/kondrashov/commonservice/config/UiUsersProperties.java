package ru.kondrashov.commonservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Staff accounts allowed to sign in to the web UI. Passwords must be stored pre-encoded
 * with a {@code {id}} prefix understood by the delegating password encoder (e.g. {@code {bcrypt}...});
 * plain-text and {@code {noop}} passwords are rejected at startup.
 */
@Data
@ConfigurationProperties(prefix = "common.security")
public class UiUsersProperties {

    static final List<String> SUPPORTED_ROLES = Collections.unmodifiableList(
            Arrays.asList(SecurityConfig.ROLE_STAFF, SecurityConfig.ROLE_ADMIN));

    private static final Pattern ENCODED_PASSWORD = Pattern.compile("^\\{(?!noop})[^{}]+}.+$");

    private List<UiUser> users = new ArrayList<>();

    @Data
    public static class UiUser {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();

        UserDetails toUserDetails() {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalStateException("common.security.users[].username must not be blank");
            }
            if (password == null || !ENCODED_PASSWORD.matcher(password).matches()) {
                throw new IllegalStateException("Password for UI user '" + username
                        + "' must be pre-encoded with an {id} prefix such as {bcrypt}; plain-text/{noop} passwords are not allowed");
            }
            if (roles == null || roles.isEmpty()) {
                throw new IllegalStateException("UI user '" + username + "' must have at least one role of " + SUPPORTED_ROLES);
            }
            for (String role : roles) {
                if (!SUPPORTED_ROLES.contains(role)) {
                    throw new IllegalStateException("UI user '" + username + "' has unsupported role '" + role
                            + "'; supported roles are " + SUPPORTED_ROLES);
                }
            }
            return User.withUsername(username)
                    .password(password)
                    .roles(roles.toArray(new String[0]))
                    .build();
        }
    }
}
