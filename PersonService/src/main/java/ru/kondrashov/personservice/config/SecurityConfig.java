package ru.kondrashov.personservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every endpoint requires an authenticated API client (HTTP Basic, stateless).
 * Clients and their roles come from {@code person-service.security.clients}; there are no default credentials.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiClientProperties.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final ApiClientProperties apiClientProperties;

    public static final String PEOPLE_READ = "PEOPLE_READ";
    public static final String PEOPLE_WRITE = "PEOPLE_WRITE";
    public static final String PEOPLE_ADMIN = "PEOPLE_ADMIN";

    static final int MIN_PASSWORD_LENGTH = 16;
    private static final Set<String> KNOWN_ROLES = new HashSet<>(Arrays.asList(PEOPLE_READ, PEOPLE_WRITE, PEOPLE_ADMIN));

    public SecurityConfig(ApiClientProperties apiClientProperties) {
        this.apiClientProperties = apiClientProperties;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .httpBasic()
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.GET, "/v1/people").hasRole(PEOPLE_ADMIN)
                .antMatchers(HttpMethod.DELETE, "/v1/people/**").hasRole(PEOPLE_ADMIN)
                .antMatchers(HttpMethod.POST, "/v1/people").hasAnyRole(PEOPLE_WRITE, PEOPLE_ADMIN)
                .antMatchers(HttpMethod.PUT, "/v1/people/*").hasAnyRole(PEOPLE_WRITE, PEOPLE_ADMIN)
                .antMatchers(HttpMethod.GET, "/v1/people/*").hasAnyRole(PEOPLE_READ, PEOPLE_WRITE, PEOPLE_ADMIN)
                .antMatchers("/v1/people", "/v1/people/**").denyAll()
                .anyRequest().authenticated();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService apiClientDetailsService(PasswordEncoder passwordEncoder) {
        return apiClients(apiClientProperties, passwordEncoder);
    }

    static InMemoryUserDetailsManager apiClients(ApiClientProperties properties, PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        for (ApiClientProperties.Client client : properties.getClients()) {
            if (!StringUtils.hasText(client.getUsername())) {
                throw new IllegalStateException("person-service.security.clients[].username must be set");
            }
            if (client.getPassword() == null || client.getPassword().length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("Password for API client '" + client.getUsername()
                        + "' must be set and at least " + MIN_PASSWORD_LENGTH + " characters long");
            }
            if (client.getRoles().isEmpty() || !KNOWN_ROLES.containsAll(client.getRoles())) {
                throw new IllegalStateException("API client '" + client.getUsername()
                        + "' must have roles from " + KNOWN_ROLES + ", got " + client.getRoles());
            }
            users.add(User.withUsername(client.getUsername())
                    .password(encoder.encode(client.getPassword()))
                    .roles(client.getRoles().toArray(new String[0]))
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
