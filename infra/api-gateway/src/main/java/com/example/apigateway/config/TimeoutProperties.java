package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "gateway.timeout")
public class TimeoutProperties {

    private boolean enabled = false;
    private Duration globalResponseTimeout = Duration.ofSeconds(5);
    private Duration connectTimeout = Duration.ofSeconds(1);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getGlobalResponseTimeout() { return globalResponseTimeout; }
    public void setGlobalResponseTimeout(Duration globalResponseTimeout) { this.globalResponseTimeout = globalResponseTimeout; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
}
