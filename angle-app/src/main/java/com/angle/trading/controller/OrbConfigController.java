package com.angle.trading.controller;

import com.angle.trading.orb.OrbSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Runtime ORB config toggles — all 4 knobs flip without restart:
 *   orbCadence       → how often ORB signal is re-evaluated
 *   adxCadence       → how often ADX is recomputed
 *   adxThreshold     → minimum ADX for signal to confirm
 *   combinationMode  → REQUIRE_BOTH / ORB_ONLY / ADX_ONLY
 *
 *   GET  /api/orb/config         → JSON of current settings
 *   POST /api/orb/config/set?key=X&value=Y
 *                                → HTMX returns refreshed card; else JSON
 */
@Slf4j
@Controller
@RequestMapping("/api/orb/config")
@RequiredArgsConstructor
public class OrbConfigController {

    private final OrbSettings settings;

    @GetMapping
    @ResponseBody
    public Map<String, Object> all() {
        return current();
    }

    @PostMapping("/set")
    public Object set(@RequestParam String key,
                      @RequestParam String value,
                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                      Model model) {
        try {
            switch (key) {
                case "orbCadence" -> settings.setOrbCadence(OrbSettings.Cadence.valueOf(value));
                case "adxCadence" -> settings.setAdxCadence(OrbSettings.Cadence.valueOf(value));
                case "adxThreshold" -> settings.setAdxThreshold(new BigDecimal(value));
                case "combinationMode" -> settings.setCombinationMode(OrbSettings.CombinationMode.valueOf(value));
                default -> throw new IllegalArgumentException("Unknown key: " + key);
            }
            log.info("ORB config updated: {} = {}", key, value);
        } catch (Exception e) {
            log.warn("ORB config update failed for {}={}: {}", key, value, e.getMessage());
            if (hxRequest == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "error", e.getMessage()));
            }
        }

        if (hxRequest != null) {
            model.addAttribute("orbCfg", current());
            return "fragments/orb-config :: orbConfig";
        }
        return ResponseEntity.ok(Map.of("ok", true, "settings", current()));
    }

    @PostMapping("/reset")
    public Object reset(@RequestHeader(value = "HX-Request", required = false) String hxRequest,
                        Model model) {
        settings.setOrbCadence(OrbSettings.Cadence.TF_BOUNDARY);
        settings.setAdxCadence(OrbSettings.Cadence.TF_BOUNDARY);
        settings.setAdxThreshold(new BigDecimal("25"));
        settings.setCombinationMode(OrbSettings.CombinationMode.REQUIRE_BOTH);
        log.info("ORB config reset to defaults");

        if (hxRequest != null) {
            model.addAttribute("orbCfg", current());
            return "fragments/orb-config :: orbConfig";
        }
        return ResponseEntity.ok(Map.of("ok", true, "settings", current()));
    }

    private Map<String, Object> current() {
        return Map.of(
                "orbCadence",       settings.getOrbCadence().name(),
                "adxCadence",       settings.getAdxCadence().name(),
                "adxThreshold",     settings.getAdxThreshold().toPlainString(),
                "combinationMode",  settings.getCombinationMode().name(),
                "cadenceChoices",   new String[]{ "TF_BOUNDARY", "EVERY_1M" },
                "thresholdChoices", new String[]{ "20", "25", "30", "35", "0" },
                "modeChoices",      new String[]{ "REQUIRE_BOTH", "ORB_ONLY", "ADX_ONLY" }
        );
    }
}
