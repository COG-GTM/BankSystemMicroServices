package ru.kondrashov.accountservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AccountServiceClientProperties.class)
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String CLIENT_ROLE = "ACCOUNT_SERVICE_CLIENT";

    private final AccountServiceClientProperties clientProperties;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Stateless REST API authenticated with HTTP Basic per request; no session cookies, so CSRF does not apply.
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/v1/people/**").hasRole(CLIENT_ROLE)
                .antMatchers("/v1", "/swagger-ui.html", "/webjars/**", "/swagger-resources/**", "/v2/api-docs", "/error").permitAll()
                .anyRequest().authenticated()
                .and()
                .httpBasic();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(clientProperties.getUsername())
                        .password(passwordEncoder.encode(clientProperties.getPassword()))
                        .roles(CLIENT_ROLE)
                        .build());
    }
}
