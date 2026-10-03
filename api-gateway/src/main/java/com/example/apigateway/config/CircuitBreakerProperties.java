package com.example.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway.circuitbreaker")
public class CircuitBreakerProperties {

    private boolean enabled = false;
    private Instance defaults = new Instance();
    private Map<String, Instance> instances = new HashMap<>();

    public static class Instance {
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private int failureRateThreshold = 50;
        private int slowCallRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(2);
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);
        private int permittedCallsInHalfOpenState = 3;
        private Bulkhead bulkhead = new Bulkhead();

        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }

        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }

        public int getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(int failureRateThreshold) { this.failureRateThreshold = failureRateThreshold; }

        public int getSlowCallRateThreshold() { return slowCallRateThreshold; }
        public void setSlowCallRateThreshold(int slowCallRateThreshold) { this.slowCallRateThreshold = slowCallRateThreshold; }

        public Duration getSlowCallDurationThreshold() { return slowCallDurationThreshold; }
        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) { this.slowCallDurationThreshold = slowCallDurationThreshold; }

        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) { this.waitDurationInOpenState = waitDurationInOpenState; }

        public int getPermittedCallsInHalfOpenState() { return permittedCallsInHalfOpenState; }
        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) { this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState; }

        public Bulkhead getBulkhead() { return bulkhead; }
        public void setBulkhead(Bulkhead bulkhead) { this.bulkhead = bulkhead; }
    }

    /**
     * Semaphore-based concurrent-call limit per CB instance.
     * ThreadPoolBulkhead is a Resilience4j alternative, but wrong for a reactive
     * gateway (dedicated thread pool defeats Netty's non-blocking model).
     */
    public static class Bulkhead {
        private int maxConcurrentCalls = 10;
        private Duration maxWaitDuration = Duration.ZERO;   // 0 = fast-fail, no queueing

        public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
        public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }

        public Duration getMaxWaitDuration() { return maxWaitDuration; }
        public void setMaxWaitDuration(Duration maxWaitDuration) { this.maxWaitDuration = maxWaitDuration; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instance getDefaults() { return defaults; }
    public void setDefaults(Instance defaults) { this.defaults = defaults; }

    public Map<String, Instance> getInstances() { return instances; }
    public void setInstances(Map<String, Instance> instances) { this.instances = instances; }
}
