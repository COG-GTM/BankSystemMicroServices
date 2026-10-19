package ru.kondrashov.accountservice.config;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * springfox-swagger-ui ships swagger-ui.html and its webjar as static resources, which Spring Boot
 * serves even when Swagger is not enabled. Outside the dev profile, answer 404 for every Swagger path.
 */
@Component
@Profile("!" + SwaggerConfig.SWAGGER_PROFILE)
public class SwaggerDisabledFilter extends OncePerRequestFilter {

    static final List<String> SWAGGER_PATH_PREFIXES = Collections.unmodifiableList(Arrays.asList(
            "/swagger-ui",
            "/swagger-resources",
            "/v2/api-docs",
            "/webjars/springfox-swagger-ui"
    ));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return SWAGGER_PATH_PREFIXES.stream().noneMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }
}
