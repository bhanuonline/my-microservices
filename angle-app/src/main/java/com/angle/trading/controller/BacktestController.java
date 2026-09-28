package com.angle.trading.controller;

import com.angle.trading.backtest.PipelineBacktestRequest;
import com.angle.trading.backtest.PipelineBacktestResult;
import com.angle.trading.backtest.PipelineBacktestService;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BacktestCardProperties;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.Map;

/**
 * Pipeline backtest endpoints.
 *
 *   GET  /admin/backtest                  → form page (choose instrument + params)
 *   POST /api/backtest/pipeline           → run, return JSON
 *   POST /admin/backtest/run              → run, render results HTML
 */
@Controller
@RequiredArgsConstructor
public class BacktestController {

    private final PipelineBacktestService backtestService;
    private final InstrumentService instrumentService;
    private final BacktestCardProperties cardProperties;

    @GetMapping("/admin/backtest")
    public String form(Model model) {
        model.addAttribute("enabled",     cardProperties.isEnabled());
        model.addAttribute("instruments", instrumentService.listEnabled());
        model.addAttribute("result",      null);
        return "admin/backtest";
    }

    @PostMapping("/admin/backtest/run")
    public String runHtml(@RequestParam String symbolToken,
                          @RequestParam(defaultValue = "NSE") String exchange,
                          @RequestParam(defaultValue = "FIVE_MINUTE") String interval,
                          @RequestParam(defaultValue = "30") int daysBack,
                          @RequestParam(defaultValue = "3") int minAgreement,
                          @RequestParam(defaultValue = "PERCENT") String trailingMode,
                          @RequestParam(defaultValue = "0.40") double trailingPercent,
                          @RequestParam(defaultValue = "0.30") double trailingActivation,
                          @RequestParam(defaultValue = "48") int expiryBars,
                          @RequestParam(defaultValue = "500000") double capital,
                          @RequestParam(defaultValue = "1.0") double riskPercent,
                          @RequestParam(defaultValue = "1") int lotSize,
                          @RequestParam(defaultValue = "RISK_BASED") String sizingMode,
                          @RequestParam(defaultValue = "1") int fixedLots,
                          Model model) {
        model.addAttribute("enabled",     cardProperties.isEnabled());
        model.addAttribute("instruments", instrumentService.listEnabled());
        model.addAttribute("selToken",    symbolToken);
        model.addAttribute("selInterval", interval);
        model.addAttribute("selDays",     daysBack);
        model.addAttribute("selMinAgreement", minAgreement);
        model.addAttribute("selLotSize",  lotSize);
        model.addAttribute("selSizingMode", sizingMode);
        model.addAttribute("selFixedLots", fixedLots);

        // Refuse to run when the feature is disabled — same page, error banner.
        if (!cardProperties.isEnabled()) {
            model.addAttribute("result", null);
            model.addAttribute("disabledError", "Backtest is disabled. Enable it from the dashboard card first.");
            return "admin/backtest";
        }

        PipelineBacktestResult result = doRun(symbolToken, exchange, interval, daysBack,
                minAgreement, trailingMode, trailingPercent, trailingActivation,
                expiryBars, capital, riskPercent, lotSize, sizingMode, fixedLots);
        model.addAttribute("result", result);
        return "admin/backtest";
    }

    @PostMapping("/api/backtest/pipeline")
    @ResponseBody
    public ResponseEntity<?> runJson(@RequestParam String symbolToken,
                                   @RequestParam(defaultValue = "NSE") String exchange,
                                   @RequestParam(defaultValue = "FIVE_MINUTE") String interval,
                                   @RequestParam(defaultValue = "30") int daysBack,
                                   @RequestParam(defaultValue = "3") int minAgreement,
                                   @RequestParam(defaultValue = "PERCENT") String trailingMode,
                                   @RequestParam(defaultValue = "0.40") double trailingPercent,
                                   @RequestParam(defaultValue = "0.30") double trailingActivation,
                                   @RequestParam(defaultValue = "48") int expiryBars,
                                   @RequestParam(defaultValue = "500000") double capital,
                                   @RequestParam(defaultValue = "1.0") double riskPercent,
                                   @RequestParam(defaultValue = "1") int lotSize,
                                   @RequestParam(defaultValue = "RISK_BASED") String sizingMode,
                                   @RequestParam(defaultValue = "1") int fixedLots) {
        if (!cardProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "ok",      false,
                            "error",   "backtest_disabled",
                            "message", "Backtest is disabled. Enable the Backtest card on the dashboard first."));
        }
        PipelineBacktestResult result = doRun(symbolToken, exchange, interval, daysBack, minAgreement,
                trailingMode, trailingPercent, trailingActivation, expiryBars, capital, riskPercent,
                lotSize, sizingMode, fixedLots);
        return ResponseEntity.ok(result);
    }

    private PipelineBacktestResult doRun(String symbolToken, String exchange, String interval, int daysBack,
                                  int minAgreement, String trailingMode, double trailingPercent,
                                  double trailingActivation, int expiryBars, double capital, double riskPercent,
                                  int lotSize, String sizingMode, int fixedLots) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(daysBack);
        PipelineBacktestRequest req = new PipelineBacktestRequest(
                symbolToken, null, Exchange.valueOf(exchange), Interval.valueOf(interval),
                from, to, minAgreement, trailingMode, trailingPercent, trailingActivation,
                expiryBars, capital, riskPercent,
                lotSize, sizingMode, fixedLots);
        return backtestService.run(req);
    }
}
