package ru.kondrashov.commonservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(UiUsersProperties.class)
public class SecurityConfig {

    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_ADMIN = "ADMIN";

    static final String PEOPLE_PAGE = "/common/v1/info/people";

    @Bean
    public SecurityFilterChain uiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeRequests(authorize -> authorize
                        .mvcMatchers("/login", "/error").permitAll()
                        .mvcMatchers("/common/v1/delete/**").hasRole(ROLE_ADMIN)
                        .mvcMatchers("/common/v1/**").hasAnyRole(ROLE_STAFF, ROLE_ADMIN)
                        .anyRequest().denyAll())
                .formLogin(form -> form
                        .defaultSuccessUrl(PEOPLE_PAGE)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UiUsersProperties properties) {
        if (properties.getUsers().isEmpty()) {
            throw new IllegalStateException("No web UI users configured. Define common.security.users[n].username, "
                    + ".password ({bcrypt}-encoded) and .roles (STAFF and/or ADMIN), e.g. via COMMON_SECURITY_USERS_0_* environment variables");
        }
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        properties.getUsers().forEach(user -> manager.createUser(user.toUserDetails()));
        return manager;
    }
}
