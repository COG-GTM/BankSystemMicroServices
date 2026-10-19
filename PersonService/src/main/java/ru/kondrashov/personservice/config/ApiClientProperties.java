package ru.kondrashov.personservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "person-service.security")
public class ApiClientProperties {

    private List<Client> clients = new ArrayList<>();

    @Data
    public static class Client {
        private String username;
        private String password;
        private List<String> roles = new ArrayList<>();
    }
}
