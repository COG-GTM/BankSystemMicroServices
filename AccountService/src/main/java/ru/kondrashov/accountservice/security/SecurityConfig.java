package ru.kondrashov.accountservice.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AccountServiceSecurityProperties.class)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final String PERSON_ACCESS = "@personAccessPolicy.canAccessPerson(authentication, #personId)";
    private static final Pattern ENCODED_PASSWORD = Pattern.compile("^\\{[^}]+}.+");

    private final AccountServiceSecurityProperties securityProperties;

    public SecurityConfig(AccountServiceSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
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
                .mvcMatchers("/swagger-ui.html", "/v2/api-docs", "/swagger-resources/**", "/webjars/**").permitAll()
                .mvcMatchers(HttpMethod.GET, "/v1/people/{personId}/**").access(PERSON_ACCESS)
                .mvcMatchers(HttpMethod.PUT,
                        "/v1/people/{personId}/accounts/{accountId}/bills/{billId}/payments",
                        "/v1/people/{personId}/accounts/{accountId}/bills/{sourceId}/transfer/{beneficiaryBillId}")
                .access(PERSON_ACCESS)
                .mvcMatchers("/v1/**").hasRole(ClientRole.ADMIN.name())
                .anyRequest().denyAll();
    }

    @Bean
    public PersonAccessPolicy personAccessPolicy() {
        return new PersonAccessPolicy();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService accountServiceClients(PasswordEncoder passwordEncoder) {
        Map<String, AccountServicePrincipal> principals = buildPrincipals(securityProperties, passwordEncoder);
        return username -> {
            AccountServicePrincipal principal = principals.get(username);
            if (principal == null) {
                throw new UsernameNotFoundException("Unknown client");
            }
            return new AccountServicePrincipal(principal.getUsername(), principal.getPassword(), principal.getRole(), principal.getPersonId());
        };
    }

    static Map<String, AccountServicePrincipal> buildPrincipals(AccountServiceSecurityProperties properties, PasswordEncoder encoder) {
        Map<String, AccountServicePrincipal> principals = new HashMap<>();
        for (AccountServiceSecurityProperties.Client client : properties.getClients()) {
            if (!StringUtils.hasText(client.getUsername()) || !StringUtils.hasText(client.getPassword())) {
                continue;
            }
            if (client.getRole() == null) {
                throw new IllegalStateException("account-service.security client '" + client.getUsername() + "' has no role");
            }
            if (client.getRole() == ClientRole.CUSTOMER && client.getPersonId() == null) {
                throw new IllegalStateException("account-service.security CUSTOMER client '" + client.getUsername() + "' has no person-id");
            }
            if (principals.containsKey(client.getUsername())) {
                throw new IllegalStateException("account-service.security client '" + client.getUsername() + "' is defined twice");
            }
            String password = ENCODED_PASSWORD.matcher(client.getPassword()).matches()
                    ? client.getPassword()
                    : encoder.encode(client.getPassword());
            principals.put(client.getUsername(), new AccountServicePrincipal(client.getUsername(), password, client.getRole(), client.getPersonId()));
        }
        return Collections.unmodifiableMap(principals);
    }
}
