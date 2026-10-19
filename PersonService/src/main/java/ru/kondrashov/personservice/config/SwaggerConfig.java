package ru.kondrashov.personservice.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletResponse;

import static springfox.documentation.builders.PathSelectors.regex;

/**
 * Swagger exposes an interactive client for the whole API, so it is only
 * available when the "{@value #SWAGGER_PROFILE}" profile is active.
 */
@Configuration
public class SwaggerConfig {

    public static final String SWAGGER_PROFILE = "dev";

    @Configuration
    @Profile(SWAGGER_PROFILE)
    @EnableSwagger2
    static class SwaggerEnabled {

        @Bean
        public Docket api()
        {
            return new Docket(DocumentationType.SWAGGER_2)
                    .select()
                    .apis(RequestHandlerSelectors.any())
                    .paths(regex("/v1.*"))
                    .build().apiInfo(apiInfo());
        }

        private ApiInfo apiInfo()
        {
            ApiInfo apiInfo = new ApiInfo(
                    "Person service",
                    "",
                    "version-1",
                    "API TOS",
                    "mikhail.a.kondrashov@gmail.com",
                    "API License",
                    "API License URL"
            );
            return apiInfo;
        }
    }

    /**
     * springfox-swagger-ui ships swagger-ui.html as a static classpath resource,
     * which Spring Boot serves regardless of profile; block it explicitly.
     */
    @Configuration
    @Profile("!" + SWAGGER_PROFILE)
    static class SwaggerDisabled {

        @Bean
        public FilterRegistrationBean<Filter> swaggerResourcesBlocker()
        {
            Filter notFound = (request, response, chain) ->
                    ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
            FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(notFound);
            registration.addUrlPatterns(
                    "/swagger-ui.html",
                    "/webjars/springfox-swagger-ui/*",
                    "/swagger-resources",
                    "/swagger-resources/*",
                    "/v2/api-docs"
            );
            return registration;
        }
    }
}
