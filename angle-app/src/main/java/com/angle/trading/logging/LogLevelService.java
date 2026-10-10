package com.angle.trading.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime log-level toggle backed by Logback.
 *
 * Spring Boot ships with Logback as the default SLF4J implementation. We cast
 * the SLF4J {@code LoggerFactory} to Logback's {@code LoggerContext} to flip
 * levels on live loggers without touching application.properties or restarting.
 *
 * Set a level to {@code null} to make the logger inherit from its parent.
 *
 * Change persists only for the JVM's lifetime. Property-file settings take
 * effect again on the next restart — which is what we want (dashboard is for
 * one-off diagnostics, not permanent config).
 */
@Slf4j
@Service
public class LogLevelService {

    /** Packages shown in the dashboard card. Add here to expose more toggles. */
    public static final List<String> MANAGED_PACKAGES = List.of(
            "com.angle.trading.orb",
            "com.angle.trading.broker.angel",
            "com.angle.trading.broker.angel.stream",
            "com.angle.trading.marketdata",
            "com.angle.trading",
            "org.springframework.security",
            "org.springframework.web",
            "org.hibernate.SQL",
            "ROOT"
    );

    /** Friendly label per package — shown as the row title in the dashboard. */
    public static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("com.angle.trading.orb",                  "ORB Signals"),
            Map.entry("com.angle.trading.broker.angel",         "Angel REST"),
            Map.entry("com.angle.trading.broker.angel.stream",  "Angel WebSocket"),
            Map.entry("com.angle.trading.marketdata",           "Market Data"),
            Map.entry("com.angle.trading",                      "Whole App"),
            Map.entry("org.springframework.security",           "Spring Security"),
            Map.entry("org.springframework.web",                "Spring Web"),
            Map.entry("org.hibernate.SQL",                      "SQL Queries"),
            Map.entry("ROOT",                                   "Root (everything)")
    );

    /** Allowed levels in UI order (loud → quiet). */
    public static final List<String> LEVELS = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF");

    /**
     * Current effective level per managed package. "effective" means the level
     * actually in use — may be inherited from a parent if not set directly.
     */
    public Map<String, String> currentLevels() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Map<String, String> out = new LinkedHashMap<>();
        for (String pkg : MANAGED_PACKAGES) {
            Logger logger = ctx.getLogger(pkg);
            Level eff = logger.getEffectiveLevel();
            out.put(pkg, eff == null ? "INHERITED" : eff.toString());
        }
        return out;
    }

    /** Change the level of {@code pkg} to {@code level}. Returns the new effective level. */
    public String setLevel(String pkg, String level) {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = ctx.getLogger(pkg);
        Level target = toLevel(level);
        logger.setLevel(target);
        String eff = logger.getEffectiveLevel() == null ? "INHERITED" : logger.getEffectiveLevel().toString();
        log.info("Log level changed: {} -> {} (effective={})", pkg, level, eff);
        return eff;
    }

    private static Level toLevel(String level) {
        if (level == null || level.isBlank() || "INHERIT".equalsIgnoreCase(level)) return null;
        return Level.valueOf(level.toUpperCase());
    }
}
