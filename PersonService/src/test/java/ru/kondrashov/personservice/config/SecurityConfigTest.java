package ru.kondrashov.personservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private static ApiClientProperties clients(String username, String password, String... roles) {
        ApiClientProperties.Client client = new ApiClientProperties.Client();
        client.setUsername(username);
        client.setPassword(password);
        client.setRoles(Arrays.asList(roles));
        ApiClientProperties properties = new ApiClientProperties();
        properties.setClients(Collections.singletonList(client));
        return properties;
    }

    @Test
    void missingOrShortPasswordFailsStartup() {
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients("common-service", null, "PEOPLE_ADMIN"), encoder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients("common-service", "", "PEOPLE_ADMIN"), encoder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients("common-service", "12345", "PEOPLE_ADMIN"), encoder))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankUsernameOrUnknownRoleFailsStartup() {
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients(" ", "a-long-enough-password", "PEOPLE_ADMIN"), encoder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients("svc", "a-long-enough-password"), encoder))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SecurityConfig.apiClients(clients("svc", "a-long-enough-password", "ADMIN"), encoder))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordsAreStoredHashed() {
        InMemoryUserDetailsManager users = SecurityConfig.apiClients(clients("svc", "a-long-enough-password", "PEOPLE_READ"), encoder);

        String stored = users.loadUserByUsername("svc").getPassword();
        assertThat(stored).isNotEqualTo("a-long-enough-password");
        assertThat(encoder.matches("a-long-enough-password", stored)).isTrue();
        assertThat(users.loadUserByUsername("svc").getAuthorities()).extracting("authority").containsExactly("ROLE_PEOPLE_READ");
    }
}
