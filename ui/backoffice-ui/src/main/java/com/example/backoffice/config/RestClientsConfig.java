package com.example.backoffice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;

@Configuration
public class RestClientsConfig {

    @Bean
    public RestTemplate backendRestTemplate(RestTemplateBuilder builder, BackendProperties props) {
        ClientHttpRequestInterceptor basicAuth = (request, body, execution) -> {
            if (!request.getHeaders().containsKey("Authorization")) {
                String creds = props.getAdminUser() + ":" + props.getAdminPassword();
                String enc = Base64.getEncoder().encodeToString(creds.getBytes());
                request.getHeaders().set("Authorization", "Basic " + enc);
            }
            return execution.execute(request, body);
        };
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(8))
                .additionalInterceptors(basicAuth)
                .build();
    }
}
