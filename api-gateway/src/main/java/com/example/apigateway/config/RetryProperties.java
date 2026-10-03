package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed mirror of retry knobs. The actual Retry filter args live in
 * spring.cloud.gateway.routes[].filters (Spring Cloud Gateway parses those at
 * route-build time). Kept here so admin endpoints / @RefreshScope work later.
 */
@ConfigurationProperties(prefix = "gateway.retry")
public class RetryProperties {

    private boolean enabled = false;
    private RetryConfig defaults = new RetryConfig();
    private Map<String, RetryConfig> instances = new HashMap<>();

    public static class RetryConfig {
        private int retries = 3;
        private List<HttpMethod> methods = List.of(HttpMethod.GET);
        private List<HttpStatus.Series> series = List.of(HttpStatus.Series.SERVER_ERROR);
        private List<String> exceptions = List.of(
                "java.io.IOException",
                "java.util.concurrent.TimeoutException");
        private BackoffConfig backoff = new BackoffConfig();

        public int getRetries() { return retries; }
        public void setRetries(int retries) { this.retries = retries; }

        public List<HttpMethod> getMethods() { return methods; }
        public void setMethods(List<HttpMethod> methods) { this.methods = methods; }

        public List<HttpStatus.Series> getSeries() { return series; }
        public void setSeries(List<HttpStatus.Series> series) { this.series = series; }

        public List<String> getExceptions() { return exceptions; }
        public void setExceptions(List<String> exceptions) { this.exceptions = exceptions; }

        public BackoffConfig getBackoff() { return backoff; }
        public void setBackoff(BackoffConfig backoff) { this.backoff = backoff; }
    }

    public static class BackoffConfig {
        private Duration firstBackoff = Duration.ofMillis(100);
        private Duration maxBackoff = Duration.ofSeconds(2);
        private int factor = 2;
        private boolean basedOnPreviousValue = false;

        public Duration getFirstBackoff() { return firstBackoff; }
        public void setFirstBackoff(Duration firstBackoff) { this.firstBackoff = firstBackoff; }

        public Duration getMaxBackoff() { return maxBackoff; }
        public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }

        public int getFactor() { return factor; }
        public void setFactor(int factor) { this.factor = factor; }

        public boolean isBasedOnPreviousValue() { return basedOnPreviousValue; }
        public void setBasedOnPreviousValue(boolean basedOnPreviousValue) { this.basedOnPreviousValue = basedOnPreviousValue; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public RetryConfig getDefaults() { return defaults; }
    public void setDefaults(RetryConfig defaults) { this.defaults = defaults; }

    public Map<String, RetryConfig> getInstances() { return instances; }
    public void setInstances(Map<String, RetryConfig> instances) { this.instances = instances; }
}
