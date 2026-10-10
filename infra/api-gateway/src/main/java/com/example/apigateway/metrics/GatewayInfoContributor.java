package com.example.apigateway.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes a snapshot of enabled gateway features at /actuator/info.features.
 *
 * Reads the same @ConditionalOnProperty flags used to wire each feature bean.
 * Result: one glance at /actuator/info tells you which layers are currently
 * active — great for incident response (someone disabled idempotency? kill
 * switch fired for canary? env drift between prod and staging?).
 */
@Component
public class GatewayInfoContributor implements InfoContributor {

    @Value("${gateway.ratelimit.enabled:false}")       private boolean rateLimit;
    @Value("${gateway.circuitbreaker.enabled:false}")  private boolean circuitBreaker;
    @Value("${gateway.retry.enabled:false}")           private boolean retry;
    @Value("${gateway.timeout.enabled:false}")         private boolean timeout;
    @Value("${gateway.idempotency.enabled:false}")     private boolean idempotency;
    @Value("${gateway.response-cache.enabled:false}")  private boolean responseCache;
    @Value("${gateway.body-logging.enabled:false}")    private boolean bodyLogging;
    @Value("${gateway.apikey.enabled:false}")          private boolean apiKey;
    @Value("${gateway.dynamic-routes.enabled:false}")  private boolean dynamicRoutes;
    @Value("${gateway.dynamic-routes.pubsub.enabled:false}") private boolean dynamicRoutesPubsub;
    @Value("${gateway.cors.enabled:false}")            private boolean cors;
    @Value("${gateway.canary.enabled:false}")          private boolean canary;
    @Value("${gateway.admin.ui:REACT}")                private String adminUi;

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("rateLimit", rateLimit);
        features.put("circuitBreaker", circuitBreaker);
        features.put("retry", retry);
        features.put("timeout", timeout);
        features.put("idempotency", idempotency);
        features.put("responseCache", responseCache);
        features.put("bodyLogging", bodyLogging);
        features.put("apiKey", apiKey);
        features.put("dynamicRoutes", dynamicRoutes);
        features.put("dynamicRoutesPubsub", dynamicRoutesPubsub);
        features.put("cors", cors);
        features.put("canary", canary);
        features.put("adminUi", adminUi);

        builder.withDetail("features", features);
    }
}
