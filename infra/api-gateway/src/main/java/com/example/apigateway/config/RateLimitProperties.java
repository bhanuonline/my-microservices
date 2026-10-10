package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.ratelimit")
public class RateLimitProperties {

    private boolean enabled = false;

    private KeyStrategy keyStrategy = KeyStrategy.USER;

    private Limit defaults = new Limit(10, 20, 1);

    private Map<String, RouteLimit> routes = new HashMap<>();

    public enum KeyStrategy { USER, IP, API_KEY }

    public static class Limit {
        private int replenishRate;
        private int burstCapacity;
        private int requestedTokens;

        public Limit() {}

        public Limit(int replenishRate, int burstCapacity, int requestedTokens) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
            this.requestedTokens = requestedTokens;
        }

        public int getReplenishRate() { return replenishRate; }
        public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }

        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }

        public int getRequestedTokens() { return requestedTokens; }
        public void setRequestedTokens(int requestedTokens) { this.requestedTokens = requestedTokens; }
    }

    public static class RouteLimit {
        private boolean enabled = true;
        private Limit limit;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Limit getLimit() { return limit; }
        public void setLimit(Limit limit) { this.limit = limit; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public KeyStrategy getKeyStrategy() { return keyStrategy; }
    public void setKeyStrategy(KeyStrategy keyStrategy) { this.keyStrategy = keyStrategy; }

    public Limit getDefaults() { return defaults; }
    public void setDefaults(Limit defaults) { this.defaults = defaults; }

    public Map<String, RouteLimit> getRoutes() { return routes; }
    public void setRoutes(Map<String, RouteLimit> routes) { this.routes = routes; }
}
