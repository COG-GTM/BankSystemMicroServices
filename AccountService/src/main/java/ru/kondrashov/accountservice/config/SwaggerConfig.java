package ru.kondrashov.accountservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import static springfox.documentation.builders.PathSelectors.regex;

/**
 * Swagger documents and lets anyone invoke the money-movement API, so it is only
 * enabled for local development ({@code --spring.profiles.active=dev}).
 * In every other profile {@link SwaggerDisabledFilter} also hides the static UI.
 */
@Configuration
@Profile(SwaggerConfig.SWAGGER_PROFILE)
@EnableSwagger2
public class SwaggerConfig {

    public static final String SWAGGER_PROFILE = "dev";

    @Bean
    public Docket api()
    {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("ru.kondrashov.accountservice.controllers"))
                .paths(regex("/v1.*"))
                .build().apiInfo(apiInfo());
    }

    private ApiInfo apiInfo()
    {
        ApiInfo apiInfo = new ApiInfo(
                "Account service",
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
