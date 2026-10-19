package ru.kondrashov.accountservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * Requires callers (CommonService) to present the shared service token as a Bearer credential.
 * Only the Swagger documentation endpoints are reachable without it.
 */
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<String> PUBLIC_PATH_PREFIXES = Arrays.asList(
            "/swagger-ui.html", "/swagger-resources", "/v2/api-docs", "/webjars/");

    private final UrlPathHelper urlPathHelper = new UrlPathHelper();
    private final byte[] expectedToken;

    public ServiceAuthenticationFilter(@Value("${service.auth.token:}") String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalStateException("service.auth.token (env SERVICE_AUTH_TOKEN) must be set");
        }
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = urlPathHelper.getPathWithinApplication(request);
        return "/v1".equals(path) || PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hasValidToken(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasValidToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] presented = authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(presented, expectedToken);
    }
}
