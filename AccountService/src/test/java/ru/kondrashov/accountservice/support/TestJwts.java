package ru.kondrashov.accountservice.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.kondrashov.accountservice.security.PersonAccess;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

public final class TestJwts {

    private TestJwts() {
    }

    public static RequestPostProcessor serviceToken() {
        return jwt().jwt(token -> token.subject("common-service"))
                .authorities(new SimpleGrantedAuthority(PersonAccess.ADMIN_AUTHORITY));
    }

    public static RequestPostProcessor personToken(UUID personId) {
        return jwt().jwt(token -> token.subject("user").claim(PersonAccess.PERSON_ID_CLAIM, personId.toString()));
    }
}
