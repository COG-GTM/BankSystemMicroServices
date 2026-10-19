package ru.kondrashov.commonservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "common-service.security")
public class OperatorProperties {

    private List<Operator> users = new ArrayList<>();

    @Data
    public static class Operator {
        private String username;
        private String password;
    }
}
