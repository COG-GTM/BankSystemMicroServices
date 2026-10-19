package ru.kondrashov.personservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAuthenticationFilterTest {

    private final ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("s3cret-token");

    private MockHttpServletResponse call(String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (chain.getRequest() != null) {
            response.setStatus(200);
        }
        return response;
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        MockHttpServletResponse response = call("/v1/people", null);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
    }

    @Test
    void rejectsWrongToken() throws Exception {
        assertThat(call("/v1/people", "Bearer wrong").getStatus()).isEqualTo(401);
        assertThat(call("/v1/people", "Basic s3cret-token").getStatus()).isEqualTo(401);
        assertThat(call("/v1/people", "Bearer s3cret-token-extra").getStatus()).isEqualTo(401);
    }

    @Test
    void acceptsValidToken() throws Exception {
        assertThat(call("/v1/people", "Bearer s3cret-token").getStatus()).isEqualTo(200);
    }

    @Test
    void protectsEveryNonDocumentationPath() throws Exception {
        assertThat(call("/actuator/health", null).getStatus()).isEqualTo(401);
        assertThat(call("/v1/", null).getStatus()).isEqualTo(401);
    }

    @Test
    void leavesSwaggerDocumentationOpen() throws Exception {
        assertThat(call("/swagger-ui.html", null).getStatus()).isEqualTo(200);
        assertThat(call("/v2/api-docs", null).getStatus()).isEqualTo(200);
        assertThat(call("/v1", null).getStatus()).isEqualTo(200);
    }

    @Test
    void refusesToStartWithoutConfiguredToken() {
        assertThatThrownBy(() -> new ServiceAuthenticationFilter(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_AUTH_TOKEN");
    }
}
