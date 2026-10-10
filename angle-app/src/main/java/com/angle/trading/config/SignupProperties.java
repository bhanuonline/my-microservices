package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Public-signup gate for /auth/signup.
 *
 * enabled=false → signup page returns 404 and the form POST is rejected.
 * Lets you keep the system CLOSED after onboarding your first users.
 *
 * defaultRole = role every new signup gets. Admins can promote later.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "signup")
public class SignupProperties {

    /** Master switch. Default false — opt-in to open signup. */
    private boolean enabled = false;

    /** Role assigned to new signups (prefix "ROLE_" optional — auto-normalised). */
    private String defaultRole = "ROLE_USER";

    /** Max accounts per IP per hour. 0 = no limit. */
    private int ipRateLimitPerHour = 5;

    /** Minimum password length (also enforced by UserService). */
    private int minPasswordLength = 8;
}
