package com.example.shop.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * One RestTemplate with sensible timeouts. In prod you'd prefer WebClient /
 * RestClient and per-destination connection pools; this is enough for a demo
 * that doesn't fan out concurrently.
 *
 * The Basic-auth header is injected by an interceptor so controllers don't
 * sprinkle credentials everywhere. Admin basic auth is the demo auth path;
 * production would carry a JWT forwarded from the user's session.
 */
@Configuration
public class RestClientsConfig {

    @Bean
    public RestTemplate backendRestTemplate(RestTemplateBuilder builder,
                                            BackendProperties props) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalInterceptors((request, body, execution) -> {
                    // Only set if caller didn't already provide one.
                    if (!request.getHeaders().containsKey("Authorization")) {
                        String creds = props.getAdminUser() + ":" + props.getAdminPassword();
                        String basic = Base64.getEncoder().encodeToString(creds.getBytes());
                        request.getHeaders().set("Authorization", "Basic " + basic);
                    }
                    return Objects.requireNonNull(execution.execute(request, body));
                })
                .build();
    }
}
