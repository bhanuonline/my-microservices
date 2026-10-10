package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Session-management policy that goes BEYOND Servlet idle timeout:
 *
 *   • {@code absoluteTimeoutMinutes}
 *     Hard cap on session lifetime measured from LOGIN time (not last request).
 *     An active user who's been clicking for 8 hours still gets logged out —
 *     this limits damage if a session cookie is stolen and silently reused.
 *     0 = disabled (idle timeout only).
 *
 *   • {@code invalidSessionUrl}
 *     Where to redirect when a request carries a session cookie that the
 *     server no longer recognises (expired, invalidated by logout from
 *     another tab, server restart, etc.). Nicer than a 403.
 *
 * Idle timeout + cookie flags live under server.servlet.session.* — those
 * are read natively by Spring Boot, not through this class.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "session")
public class SessionProperties {

    /** Hard cap, in minutes, measured from login. 0 = no cap. */
    private int absoluteTimeoutMinutes = 480;

    /** URL to send users to when their session cookie is stale. */
    private String invalidSessionUrl = "/auth/login?expired";
}
