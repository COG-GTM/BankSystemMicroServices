package ru.kondrashov.accountservice.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API clients allowed to call AccountService over HTTP Basic.
 * Clients with a blank username or password are ignored, so an unconfigured service rejects every request.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "account-service.security")
public class AccountServiceSecurityProperties {

    private List<Client> clients = new ArrayList<>();

    @Getter
    @Setter
    public static class Client {
        private String username;
        /** Raw secret, or a DelegatingPasswordEncoder value such as {bcrypt}$2a$... */
        private String password;
        private ClientRole role;
        /** Required for CUSTOMER clients: the only person they may act on. */
        private UUID personId;
    }
}
