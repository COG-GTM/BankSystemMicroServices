package ru.kondrashov.commonservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Staff accounts allowed to sign in to the web UI. Passwords must be stored pre-encoded with a
 * strong adaptive hash ({@code {bcrypt}} or {@code {pbkdf2}}); plain-text, {@code {noop}}, legacy
 * digests ({@code {MD5}}, {@code {SHA-1}}, ...) and unknown encodings are rejected at startup.
 */
@Data
@ConfigurationProperties(prefix = "common.security")
public class UiUsersProperties {

    static final List<String> SUPPORTED_ROLES = Collections.unmodifiableList(
            Arrays.asList(SecurityConfig.ROLE_STAFF, SecurityConfig.ROLE_ADMIN));

    static final List<String> ALLOWED_PASSWORD_ENCODINGS = Collections.unmodifiableList(
            Arrays.asList("bcrypt", "pbkdf2"));

    private static final Pattern ENCODED_PASSWORD = Pattern.compile("^\\{([^{}]+)}.+$");

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
            Matcher encoded = password == null ? null : ENCODED_PASSWORD.matcher(password);
            if (encoded == null || !encoded.matches() || !ALLOWED_PASSWORD_ENCODINGS.contains(encoded.group(1))) {
                throw new IllegalStateException("Password for UI user '" + username + "' must be pre-encoded with one of "
                        + ALLOWED_PASSWORD_ENCODINGS + " as an {id} prefix, e.g. {bcrypt}$2a$10$...");
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
