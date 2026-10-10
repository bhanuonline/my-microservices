package com.example.resourceserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the resource-server.
 *
 * <p>resource-server is a <strong>minimal reference implementation</strong> of
 * the OAuth2 resource-server pattern in Spring Security. Its only job:
 * accept bearer-token-authenticated HTTP requests, validate the JWT against
 * the issuer's JWKS endpoint, and serve data to the authenticated principal.
 *
 * <p>Business services (user-service, product-service, order-service) use the
 * same pattern but with real domain data. This module is intentionally kept
 * small so the pattern is visible without surrounding complexity.
 *
 * <p><b>Not registered with Eureka</b> by design — this is a standalone
 * teaching artifact, not part of the gateway's routed services. If you ever
 * promote it to a real service, add
 * {@code spring-cloud-starter-netflix-eureka-client} and configure
 * {@code eureka.client.service-url.defaultZone}.
 *
 * <p><b>Historical note:</b> prior to the Tier 0 bug-fix pass, this class
 * declared a static inner {@code @RestController} that collided with
 * {@link ApiController}. Both exposed {@code /api/hello} and Spring picked
 * one unpredictably. The inner class is removed; {@link ApiController} is
 * the single source of truth for endpoints.
 */
@SpringBootApplication
public class ResourceServerApplication {

    private static final Logger log = LoggerFactory.getLogger(ResourceServerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
        log.info("resource-server application started");
    }
}
