package ru.kondrashov.accountservice.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;
import java.util.UUID;

public class AccountServicePrincipal extends User {

    private final ClientRole role;
    private final UUID personId;

    public AccountServicePrincipal(String username, String password, ClientRole role, UUID personId) {
        super(username, password, Collections.singletonList(new SimpleGrantedAuthority(role.authority())));
        this.role = role;
        this.personId = personId;
    }

    public ClientRole getRole() {
        return role;
    }

    public UUID getPersonId() {
        return personId;
    }
}
