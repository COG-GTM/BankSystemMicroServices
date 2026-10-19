package ru.kondrashov.commonservice.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

@Configuration
@EnableConfigurationProperties(DownstreamServicesProperties.class)
public class ApplicationConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder, DownstreamServicesProperties properties) {
        if (properties.getTls().isConfigured()) {
            SSLContext sslContext = TlsClientHttpRequestFactory.buildSslContext(properties.getTls());
            restTemplateBuilder = restTemplateBuilder.requestFactory(() -> new TlsClientHttpRequestFactory(sslContext));
        }
        return restTemplateBuilder.build();
    }

    @Bean
    public Logger logger(){
        return LogManager.getRootLogger();
    }
}
