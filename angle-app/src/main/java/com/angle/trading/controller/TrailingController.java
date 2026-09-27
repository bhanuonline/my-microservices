package com.angle.trading.controller;

import com.angle.trading.config.TrailingProperties;
import com.angle.trading.service.TrailingStopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Runtime toggles for the trailing-stop engine.
 *
 *   GET  /api/trailing/status              — current config snapshot
 *   POST /api/trailing/enable?on=...       — master switch
 *   POST /api/trailing/mode?value=PERCENT  — swap mode without restart
 *   POST /api/trailing/tune?...            — tune knobs on the fly
 */
@RestController
@RequestMapping("/api/trailing")
@RequiredArgsConstructor
public class TrailingController {

    private final TrailingStopService trailingStopService;
    private final TrailingProperties props;

    @GetMapping("/status")
    public TrailingStopService.Snapshot status() {
        return trailingStopService.snapshot();
    }

    @PostMapping("/enable")
    public Map<String, Object> enable(@RequestParam boolean on) {
        props.setEnabled(on);
        return Map.of("ok", true, "enabled", on);
    }

    @PostMapping("/mode")
    public Map<String, Object> mode(@RequestParam String value) {
        String v = value.toUpperCase();
        if (!v.matches("NONE|FIXED|PERCENT|MILESTONE|ATR")) {
            return Map.of("ok", false, "error", "mode must be NONE / FIXED / PERCENT / MILESTONE / ATR");
        }
        props.setMode(v);
        return Map.of("ok", true, "mode", v);
    }

    /** Any subset of knobs can be tuned in one call. Null means leave as-is. */
    @PostMapping("/tune")
    public Map<String, Object> tune(
            @RequestParam(required = false) Double activationPercent,
            @RequestParam(required = false) Double fixedDistance,
            @RequestParam(required = false) Double percentDistance,
            @RequestParam(required = false) Double atrMultiplier,
            @RequestParam(required = false) Integer atrPeriod,
            @RequestParam(required = false) String  milestoneLadder
    ) {
        if (activationPercent != null) props.setActivationPercent(activationPercent);
        if (fixedDistance   != null) props.setFixedDistance(fixedDistance);
        if (percentDistance != null) props.setPercentDistance(percentDistance);
        if (atrMultiplier   != null) props.setAtrMultiplier(atrMultiplier);
        if (atrPeriod       != null) props.setAtrPeriod(atrPeriod);
        if (milestoneLadder != null) props.setMilestoneLadder(milestoneLadder);
        return Map.of("ok", true, "snapshot", trailingStopService.snapshot());
    }
}
