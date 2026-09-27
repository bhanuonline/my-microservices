package com.angle.trading.controller;

import com.angle.trading.analysis.RegimeService;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.RegimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoints for the regime detector.
 *
 *   GET  /api/regime/status               — current state snapshot
 *   GET  /api/regime/check?token=...      — dry-run gate for one symbol (would signal fire?)
 *   POST /api/regime/reload               — re-parse allowedRegimes / allowedWindows CSVs
 *   POST /api/regime/enable?on=true|false — flip master switch at runtime
 */
@RestController
@RequestMapping("/api/regime")
@RequiredArgsConstructor
public class RegimeController {

    private final RegimeService regimeService;
    private final RegimeProperties props;

    @GetMapping("/status")
    public RegimeService.Snapshot status() {
        return regimeService.snapshot();
    }

    @GetMapping("/check")
    public Map<String, Object> check(
            @RequestParam String token,
            @RequestParam(defaultValue = "NSE") String exchange,
            @RequestParam(defaultValue = "FIVE_MINUTE") String interval
    ) {
        Exchange ex = Exchange.valueOf(exchange);
        Interval iv = Interval.valueOf(interval);
        String regime = regimeService.detectRegime(token, ex, iv, null);
        RegimeService.Decision decision = regimeService.allow(token, ex, iv, null);
        return Map.of(
                "token",    token,
                "exchange", exchange,
                "interval", interval,
                "regime",   regime,
                "allowed",  decision.allowed(),
                "reason",   decision.reason()
        );
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        regimeService.parseConfig();
        return Map.of("ok", true, "message", "regime config re-parsed", "snapshot", regimeService.snapshot());
    }

    @PostMapping("/enable")
    public Map<String, Object> enable(@RequestParam boolean on) {
        props.setEnabled(on);
        return Map.of("ok", true, "regime.enabled", on);
    }

    @PostMapping("/gate/{name}")
    public Map<String, Object> gate(@PathVariable String name, @RequestParam boolean on) {
        switch (name.toLowerCase()) {
            case "adx"  -> props.getAdx().setEnabled(on);
            case "vix"  -> props.getVix().setEnabled(on);
            case "time" -> props.getTime().setEnabled(on);
            default     -> { return Map.of("ok", false, "error", "unknown gate: " + name); }
        }
        return Map.of("ok", true, "gate", name, "enabled", on);
    }
}
