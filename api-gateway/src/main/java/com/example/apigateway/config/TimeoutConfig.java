package com.example.apigateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TimeoutProperties.class)
@ConditionalOnProperty(prefix = "gateway.timeout", name = "enabled", havingValue = "true")
public class TimeoutConfig {

    @Bean
    public HttpClientCustomizer httpClientCustomizer(TimeoutProperties props) {
        return httpClient -> httpClient
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) props.getConnectTimeout().toMillis())
                .responseTimeout(props.getGlobalResponseTimeout());
    }
}
