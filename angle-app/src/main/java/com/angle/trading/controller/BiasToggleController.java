package com.angle.trading.controller;

import com.angle.trading.config.BiasProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime on/off switch for the bias engine. Backs both:
 *   • The dashboard's HTMX toggle buttons  → returns the card HTML fragment
 *   • Programmatic use via curl / Postman  → returns JSON status
 *
 * HTMX request is detected via the {@code HX-Request} header, which htmx.org
 * sets automatically. Same URL, two return types — the client picks which by
 * how it calls the endpoint.
 */
@Controller
@RequestMapping("/api/bias")
@RequiredArgsConstructor
public class BiasToggleController {

    private final BiasProperties props;

    /** JSON status — for Postman/curl. */
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> status() {
        return Map.of("enabled", props.isEnabled());
    }

    /**
     * Toggle master switch.
     * HTMX call (HX-Request header) → returns the refreshed card fragment (String view name).
     * Anything else → returns JSON in a ResponseEntity.
     */
    @PostMapping("/enable")
    public Object enable(@RequestParam boolean on,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         Model model) {
        props.setEnabled(on);

        if (hxRequest != null) {
            // Populate the same "config" model the dashboard fragment expects,
            // then return the fragment view name. HTMX swaps it in place.
            Map<String, Object> config = new HashMap<>();
            config.put("biasEnabled",         props.isEnabled());
            config.put("refreshMinutes",      props.getRefreshMinutes());
            config.put("lookbackDays",        props.getLookbackDays());
            config.put("changeAlertsEnabled", props.getChangeAlerts().isEnabled());
            model.addAttribute("config", config);
            return "fragments/dashboard-cards :: biasEngineCard";
        }
        return ResponseEntity.ok(Map.of("ok", true, "enabled", on));
    }
}
