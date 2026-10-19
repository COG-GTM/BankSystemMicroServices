package ru.kondrashov.commonservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The web UI acts on customer PII through PersonService's service credentials, so every page requires a signed-in operator.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(OperatorProperties.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String OPERATOR = "OPERATOR";
    static final int MIN_PASSWORD_LENGTH = 16;

    private final OperatorProperties operatorProperties;

    public SecurityConfig(OperatorProperties operatorProperties) {
        this.operatorProperties = operatorProperties;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .anyRequest().hasRole(OPERATOR)
                .and()
                .formLogin()
                .defaultSuccessUrl("/common/v1/info/people")
                .permitAll()
                .and()
                .logout()
                .permitAll();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService operatorDetailsService(PasswordEncoder passwordEncoder) {
        return operators(operatorProperties, passwordEncoder);
    }

    static InMemoryUserDetailsManager operators(OperatorProperties properties, PasswordEncoder encoder) {
        List<UserDetails> users = new ArrayList<>();
        for (OperatorProperties.Operator operator : properties.getUsers()) {
            if (!StringUtils.hasText(operator.getUsername())) {
                throw new IllegalStateException("common-service.security.users[].username must be set");
            }
            if (operator.getPassword() == null || operator.getPassword().length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("Password for operator '" + operator.getUsername()
                        + "' must be set and at least " + MIN_PASSWORD_LENGTH + " characters long");
            }
            users.add(User.withUsername(operator.getUsername())
                    .password(encoder.encode(operator.getPassword()))
                    .roles(OPERATOR)
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
