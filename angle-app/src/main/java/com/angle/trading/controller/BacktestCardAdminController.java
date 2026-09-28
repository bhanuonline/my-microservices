package com.angle.trading.controller;

import com.angle.trading.config.BacktestCardProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Runtime editor for the Backtest dashboard card.
 *
 *   POST /api/backtest-card/enable?on=...  → toggle visibility
 *   POST /api/backtest-card/save?...       → update lookBack + defaultDays
 *
 * Dual-mode response (same pattern as BiasToggleController):
 *   • HTMX call (HX-Request header)  → returns the refreshed card fragment
 *   • Plain curl / Postman           → returns JSON
 *
 * HTMX swaps just this card in place without reloading the whole dashboard.
 */
@Controller
@RequiredArgsConstructor
public class BacktestCardAdminController {

    private final BacktestCardProperties props;

    /**
     * Return the edit-mode fragment (the form). Called by the gear icon.
     * If the card is currently disabled, returns the normal view instead —
     * you must enable the card before you can edit its settings.
     */
    @GetMapping("/api/backtest-card/edit")
    public String editForm(Model model) {
        model.addAttribute("backtestCard", props);
        return props.isEnabled()
                ? "fragments/dashboard-cards :: backtestCardEdit"
                : "fragments/dashboard-cards :: backtestCard";
    }

    /**
     * Return the view-mode fragment (normal card). Called by Cancel button.
     */
    @GetMapping("/api/backtest-card/view")
    public String viewCard(Model model) {
        model.addAttribute("backtestCard", props);
        return "fragments/dashboard-cards :: backtestCard";
    }

    @PostMapping("/api/backtest-card/enable")
    public Object enable(@RequestParam boolean on,
                         @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                         Model model) {
        props.setEnabled(on);
        return renderResponse(hxRequest, model, "enabled", on);
    }

    @PostMapping("/api/backtest-card/save")
    public Object save(@RequestParam(required = false) Boolean enabled,
                       @RequestParam(required = false) Integer lookBackMin,
                       @RequestParam(required = false) Integer lookBackMax,
                       @RequestParam(required = false) Integer defaultDays,
                       @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                       Model model) {
        if (enabled     != null) props.setEnabled(enabled);
        if (lookBackMin != null) props.setLookBackMin(Math.max(1, lookBackMin));
        if (lookBackMax != null) props.setLookBackMax(Math.max(props.getLookBackMin(), lookBackMax));
        if (defaultDays != null) props.setDefaultDays(
                Math.min(Math.max(defaultDays, props.getLookBackMin()), props.getLookBackMax()));
        return renderResponse(hxRequest, model, "all", null);
    }

    /**
     * HTMX → fragment (view name).
     * Plain call → JSON (ResponseEntity).
     */
    private Object renderResponse(String hxRequest, Model model, String action, Object value) {
        if (hxRequest != null) {
            model.addAttribute("backtestCard", props);
            return "fragments/dashboard-cards :: backtestCard";
        }
        return ResponseEntity.ok(Map.of(
                "ok",          true,
                "action",      action,
                "value",       value == null ? Map.of() : value,
                "enabled",     props.isEnabled(),
                "lookBackMin", props.getLookBackMin(),
                "lookBackMax", props.getLookBackMax(),
                "defaultDays", props.getDefaultDays()
        ));
    }
}
