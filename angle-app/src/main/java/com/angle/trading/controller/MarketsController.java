package com.angle.trading.controller;

import com.angle.trading.config.GlobalMarketsProperties;
import com.angle.trading.markets.GlobalMarketService;
import com.angle.trading.markets.MarketQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * Global Markets dashboard page + JSON API.
 *
 *   GET  /markets                        → HTML page with card grid
 *   GET  /api/markets/quotes             → JSON list of all quotes
 *   POST /api/markets/refresh            → force re-fetch now (bypasses schedule)
 */
@Controller
@RequiredArgsConstructor
public class MarketsController {

    private final GlobalMarketService marketService;
    private final GlobalMarketsProperties props;

    @GetMapping("/markets")
    public String page(Model model) {
        List<MarketQuote> quotes = marketService.allQuotes();
        model.addAttribute("quotes",         quotes);
        model.addAttribute("impact",         marketService.impactAnalysis());
        model.addAttribute("refreshSeconds", props.getPageRefreshSeconds());
        model.addAttribute("enabled",        props.isEnabled());
        model.addAttribute("refreshMinutes", props.getRefreshMinutes());
        return "markets/index";
    }

    @GetMapping("/api/markets/quotes")
    @ResponseBody
    public List<MarketQuote> quotes() {
        return marketService.allQuotes();
    }

    @PostMapping("/api/markets/refresh")
    @ResponseBody
    public Map<String, Object> refresh() {
        int n = marketService.refreshAll();
        return Map.of("ok", true, "refreshed", n);
    }
}
