package com.angle.trading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Provides shared RestClient beans for outbound HTTP calls.
 *
 * Two clients:
 *  - {@code restClient}           — general purpose (broker API calls). Short timeouts;
 *                                   a hung broker should not stall the app.
 *  - {@code masterFileRestClient} — for downloading Angel's ~30 MB scrip master file.
 *                                   Needs a much longer read timeout and asks for
 *                                   gzip so bandwidth-limited networks don't struggle.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(15).toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }

    @Bean
    public RestClient masterFileRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofMinutes(2).toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Encoding", "gzip")
                .build();
    }
}
