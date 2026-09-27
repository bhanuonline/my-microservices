package com.angle.trading.controller;

import com.angle.trading.analysis.MtfConfirmationService;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.MtfProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime controls for multi-timeframe confirmation.
 *
 *   GET  /api/mtf/status               — current config snapshot
 *   GET  /api/mtf/check?...            — dry-run confirmation for one symbol
 *   POST /api/mtf/enable?on=...        — master switch
 *   POST /api/mtf/mode?value=ALL|...   — swap agreement mode
 *   POST /api/mtf/method?value=...     — swap detection method
 *   POST /api/mtf/tfs?value=CSV        — replace higher-TF list
 *   POST /api/mtf/reload               — re-parse config CSVs + flush cache
 */
@RestController
@RequestMapping("/api/mtf")
@RequiredArgsConstructor
public class MtfController {

    private final MtfConfirmationService mtfService;
    private final MtfProperties props;

    @GetMapping("/status")
    public MtfConfirmationService.Snapshot status() {
        return mtfService.snapshot();
    }

    /**
     * Dry-run: for a hypothetical BUY (or SELL) on this symbol at signalTf,
     * would MTF let it through? Reports each higher-TF direction so you can see why.
     */
    @GetMapping("/check")
    public Map<String, Object> check(
            @RequestParam String token,
            @RequestParam(defaultValue = "NSE") String exchange,
            @RequestParam(defaultValue = "FIVE_MINUTE") String interval,
            @RequestParam(defaultValue = "BUY") String action
    ) {
        Exchange ex = Exchange.valueOf(exchange);
        Interval iv = Interval.valueOf(interval);
        MtfConfirmationService.Decision d = mtfService.confirm(token, ex, iv, action);

        Map<String, String> perTf = new LinkedHashMap<>();
        for (String tfName : mtfService.snapshot().higherTimeframes()) {
            try {
                Interval tf = Interval.valueOf(tfName);
                perTf.put(tfName, mtfService.directionFor(token, ex, tf));
            } catch (Exception ignored) { /* skip bad enum */ }
        }
        return Map.of(
                "token",    token,
                "action",   action,
                "signalTf", interval,
                "allowed",  d.allowed(),
                "reason",   d.reason(),
                "perTf",    perTf
        );
    }

    @PostMapping("/enable")
    public Map<String, Object> enable(@RequestParam boolean on) {
        props.setEnabled(on);
        return Map.of("ok", true, "enabled", on);
    }

    @PostMapping("/mode")
    public Map<String, Object> mode(@RequestParam String value) {
        String v = value.toUpperCase();
        if (!v.matches("ALL|MAJORITY|ANY")) {
            return Map.of("ok", false, "error", "mode must be ALL / MAJORITY / ANY");
        }
        props.setAgreementMode(v);
        return Map.of("ok", true, "agreementMode", v);
    }

    @PostMapping("/method")
    public Map<String, Object> method(@RequestParam String value) {
        String v = value.toUpperCase();
        if (!v.matches("EMA_STACK|PRICE_VS_EMA|SUPERTREND")) {
            return Map.of("ok", false, "error", "method must be EMA_STACK / PRICE_VS_EMA / SUPERTREND");
        }
        props.setMethod(v);
        mtfService.reload();
        return Map.of("ok", true, "method", v);
    }

    @PostMapping("/tfs")
    public Map<String, Object> tfs(@RequestParam String value) {
        props.setHigherTimeframes(value);
        mtfService.reload();
        return Map.of("ok", true, "higherTimeframes", value, "snapshot", mtfService.snapshot());
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        mtfService.reload();
        return Map.of("ok", true, "snapshot", mtfService.snapshot());
    }
}
