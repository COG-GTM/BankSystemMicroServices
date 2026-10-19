package ru.kondrashov.accountservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "account-service.client")
public class AccountServiceClientProperties {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
