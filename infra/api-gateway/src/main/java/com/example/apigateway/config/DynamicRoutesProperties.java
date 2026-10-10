package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.UUID;

@ConfigurationProperties(prefix = "gateway.dynamic-routes")
public class DynamicRoutesProperties {

    private boolean enabled = false;

    /**
     * Fallback poll cadence. With pub/sub enabled, this is the eventual-consistency
     * floor for missed messages (network blips, Redis restarts). Longer = less
     * Redis load; shorter = tighter correctness bound.
     */
    private Duration refreshInterval = Duration.ofMinutes(5);

    private PubSub pubsub = new PubSub();

    public static class PubSub {
        private boolean enabled = false;
        private String channel = "gateway.routes.refresh";
        /** Per-JVM identity. Messages tagged with this; receivers skip echoes matching their own id. */
        private String instanceId = UUID.randomUUID().toString();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }

        public String getInstanceId() { return instanceId; }
        public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(Duration refreshInterval) { this.refreshInterval = refreshInterval; }

    public PubSub getPubsub() { return pubsub; }
    public void setPubsub(PubSub pubsub) { this.pubsub = pubsub; }
}
