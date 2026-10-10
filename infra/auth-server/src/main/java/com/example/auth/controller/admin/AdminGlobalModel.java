package com.example.auth.controller.admin;

import com.example.auth.config.FeatureFlags;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds {@code features} to the model of every admin controller automatically.
 *
 * <p>Why: the nav bar (in {@code admin/layout.html}) needs to conditionally
 * render nav links based on which features are on. Rather than have every
 * controller manually do {@code model.addAttribute("features", flags)},
 * this @ControllerAdvice makes it a one-time wiring.
 *
 * <p>How it works: Spring MVC calls {@link #features()} before every request
 * handled by a controller under {@code com.example.auth.controller.admin}.
 * The returned value is bound to the model attribute named "features".
 * Templates just reference {@code ${features.xxx.enabled}}.
 *
 * <p>Scoped by {@code basePackages} so it doesn't accidentally add "features"
 * to OAuth flow views (login page, consent page) — those have their own model
 * shape.
 */
@ControllerAdvice(basePackages = "com.example.auth.controller.admin")
@Profile("jdbc")
public class AdminGlobalModel {

    private final FeatureFlags flags;

    public AdminGlobalModel(FeatureFlags flags) {
        this.flags = flags;
    }

    @ModelAttribute("features")
    public FeatureFlags features() {
        return flags;
    }
}
