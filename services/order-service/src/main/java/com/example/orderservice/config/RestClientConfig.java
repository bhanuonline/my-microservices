package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // RestClient (Spring 6.1) — fluent, synchronous HTTP client.
    // Modern replacement for RestTemplate. Inject this bean wherever you need
    // to make a raw HTTP call that isn't covered by a Feign client.
    //
    // Usage:
    //   String result = restClient.get()
    //       .uri("/api/v1/products/{id}", id)
    //       .retrieve()
    //       .body(String.class);
    @Bean
    public RestClient restClient() {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    // good place to add auth headers, correlation IDs, etc.
                    return execution.execute(request, body);
                })
                .build();
    }
}
