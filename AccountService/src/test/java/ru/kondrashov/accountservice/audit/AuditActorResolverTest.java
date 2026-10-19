package ru.kondrashov.accountservice.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AuditActorResolverTest {

    private final AuditActorResolver resolver = new AuditActorResolver();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MockHttpServletRequest bind() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void outsideRequest_isSystem() {
        AuditActor actor = resolver.resolve();
        assertThat(actor.getName()).isEqualTo("system");
        assertThat(actor.getSource()).isEqualTo(ActorSource.SYSTEM);
    }

    @Test
    void authenticatedPrincipal_winsOverHeader() {
        MockHttpServletRequest request = bind();
        request.setUserPrincipal(() -> "teller-7");
        request.addHeader(AuditActorResolver.ACTOR_HEADER, "someone-else");
        request.addHeader(AuditActorResolver.REQUEST_ID_HEADER, "req-1");

        AuditActor actor = resolver.resolve();

        assertThat(actor.getName()).isEqualTo("teller-7");
        assertThat(actor.getSource()).isEqualTo(ActorSource.PRINCIPAL);
        assertThat(actor.getClientAddress()).isEqualTo("10.1.2.3");
        assertThat(actor.getRequestId()).isEqualTo("req-1");
    }

    @Test
    void assertedHeader_isMarkedAsHeader() {
        bind().addHeader(AuditActorResolver.ACTOR_HEADER, "common-service");

        AuditActor actor = resolver.resolve();

        assertThat(actor.getName()).isEqualTo("common-service");
        assertThat(actor.getSource()).isEqualTo(ActorSource.HEADER);
    }

    @Test
    void noIdentity_isAnonymous() {
        bind();

        AuditActor actor = resolver.resolve();

        assertThat(actor.getName()).isEqualTo("anonymous");
        assertThat(actor.getSource()).isEqualTo(ActorSource.NONE);
        assertThat(actor.getClientAddress()).isEqualTo("10.1.2.3");
    }

    @Test
    void controlCharactersAreNeutralisedAndLengthCapped() {
        StringBuilder longValue = new StringBuilder("evil\r\n{\"forged\":true}");
        for (int i = 0; i < 200; i++) {
            longValue.append('x');
        }
        bind().addHeader(AuditActorResolver.ACTOR_HEADER, longValue.toString());

        AuditActor actor = resolver.resolve();

        assertThat(actor.getName()).doesNotContain("\r", "\n").startsWith("evil__{");
        assertThat(actor.getName()).hasSize(AuditActorResolver.MAX_VALUE_LENGTH);
    }
}
