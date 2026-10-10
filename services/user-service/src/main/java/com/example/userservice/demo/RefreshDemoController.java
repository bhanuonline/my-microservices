package com.example.userservice.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controller to show @RefreshScope in action.
 *
 * Try this:
 *   1. GET /api/v1/demo/greeting            → returns the current yml value
 *   2. Edit demo.greeting in application.yml, save (do NOT restart the service)
 *   3. POST /actuator/refresh               → returns list of refreshed keys
 *   4. GET /api/v1/demo/greeting            → returns the NEW yml value
 *
 * Without @RefreshScope, step 4 would still return the OLD value until restart.
 */
@RestController
@RefreshScope
public class RefreshDemoController {

    @Value("${demo.greeting:default}")
    private String greeting;

    @GetMapping("/api/v1/demo/greeting")
    public String greeting() {
        return greeting;
    }
}
