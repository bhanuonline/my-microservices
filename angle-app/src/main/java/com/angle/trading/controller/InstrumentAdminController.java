package com.angle.trading.controller;

import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin UI + REST API for managing tracked instruments.
 *
 * All endpoints under /admin/instruments require ROLE_ADMIN (Spring Security).
 *
 *   GET    /admin/instruments               → HTML page
 *   GET    /admin/instruments/data          → JSON list (all)
 *   POST   /admin/instruments               → create
 *   PUT    /admin/instruments/{id}          → update
 *   PUT    /admin/instruments/{id}/toggle   → flip enabled
 *   DELETE /admin/instruments/{id}          → remove
 *   GET    /admin/instruments/lookup?q=...  → autocomplete against scrip master
 */
@Slf4j
@Controller
@RequestMapping("/admin/instruments")
@RequiredArgsConstructor
public class InstrumentAdminController {

    private static final int LOOKUP_LIMIT = 15;

    private final InstrumentService service;
    private final InstrumentMasterService masterService;

    // ---------- HTML page ----------

    @GetMapping
    public String page(Model model) {
        model.addAttribute("instruments", service.listAll());
        return "admin/instruments";
    }

    // ---------- REST ----------

    @GetMapping("/data")
    @ResponseBody
    public List<BiasInstrumentEntity> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseBody
    public BiasInstrumentEntity create(@RequestBody BiasInstrumentEntity in) {
        return service.create(in);
    }

    @PutMapping("/{id}")
    @ResponseBody
    public BiasInstrumentEntity update(@PathVariable Long id, @RequestBody BiasInstrumentEntity patch) {
        return service.update(id, patch);
    }

    @PutMapping("/{id}/toggle")
    @ResponseBody
    public Map<String, Object> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        service.toggleEnabled(id, enabled);
        return Map.of("id", id, "enabled", enabled);
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return Map.of("id", id, "deleted", true);
    }

    /**
     * Bulk-seed the top 50 NSE F&O stocks.
     *
     * For each ticker in the hardcoded list, looks up "{TICKER}-EQ" in the
     * Angel scrip master (NSE cash equity segment). Skips any that are already
     * in the DB (by token). Returns a summary: added / skipped / not_found.
     */
    @PostMapping("/bulk-seed")
    @ResponseBody
    public Map<String, Object> bulkSeed() {
        // Top-50 F&O universe — NSE tickers (Angel symbol = "{TICKER}-EQ")
        List<String> tickers = List.of(
                "NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY",
                "RELIANCE", "HDFCBANK", "INFY", "TCS", "ICICIBANK",
                "KOTAKBANK", "SBIN", "AXISBANK", "BAJFINANCE", "WIPRO",
                "HCLTECH", "TATAMOTORS", "MARUTI", "SUNPHARMA", "HINDUNILVR",
                "ITC", "TITAN", "ADANIENT", "BAJAJFINSV", "DIVISLAB",
                "DRREDDY", "CIPLA", "EICHERMOT", "HEROMOTOCO", "NESTLEIND",
                "POWERGRID", "NTPC", "COALINDIA", "ONGC", "BPCL",
                "INDUSINDBK", "FEDERALBNK", "PNB", "BANKBARODA", "CANBK",
                "TATASTEEL", "JSWSTEEL", "HINDALCO", "SAIL", "VEDL",
                "ULTRACEMCO", "GRASIM", "ASIANPAINT", "PIDILITIND",
                "APOLLOHOSP", "TATACONSUM"
        );

        // Existing tokens to skip duplicates
        Set<String> existingTokens = service.listAll().stream()
                .map(BiasInstrumentEntity::getSymbolToken)
                .collect(Collectors.toSet());

        List<String> added    = new ArrayList<>();
        List<String> skipped  = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        int priority = service.listAll().stream()
                .mapToInt(BiasInstrumentEntity::getPriority).max().orElse(0) + 10;

        for (String ticker : tickers) {
            // Indices are listed directly (e.g. "NIFTY"), equities as "TICKER-EQ"
            boolean isIndex = List.of("NIFTY","BANKNIFTY","FINNIFTY","MIDCPNIFTY").contains(ticker);

            String lookupSymbol = isIndex ? ticker : (ticker + "-EQ");
            var opt = masterService.findBySymbol(lookupSymbol);

            // Fallback: name search for the first NSE EQ match
            if (opt.isEmpty()) {
                opt = masterService.searchByNameOrSymbol(ticker, 20).stream()
                        .filter(i -> "NSE".equalsIgnoreCase(i.exchSeg())
                                  && (isIndex || "EQ".equalsIgnoreCase(i.instrumentType())))
                        .findFirst();
            }

            if (opt.isEmpty()) {
                log.warn("bulkSeed: '{}' not found in scrip master", ticker);
                notFound.add(ticker);
                continue;
            }

            Instrument instr = opt.get();
            if (existingTokens.contains(instr.token())) {
                skipped.add(ticker);
                continue;
            }

            // Determine exchange
            String exchange = "NSE";
            if (instr.exchSeg() != null && !instr.exchSeg().isBlank()) {
                exchange = instr.exchSeg().toUpperCase();
            }

            BiasInstrumentEntity entity = new BiasInstrumentEntity();
            entity.setSymbol(instr.name() != null ? instr.name() : ticker);
            entity.setBroker("ANGEL");
            entity.setExchange(exchange);
            entity.setSymbolToken(instr.token());
            entity.setIntervalType("FIVE_MINUTE");
            entity.setEnabled(true);
            entity.setPriority(priority);
            entity.setLotSize(instr.lotSizeInt());

            try {
                service.create(entity);
                added.add(ticker);
                existingTokens.add(instr.token());
                priority += 10;
            } catch (Exception e) {
                log.warn("bulkSeed: failed to save '{}' — {}", ticker, e.getMessage());
                skipped.add(ticker + " (save error)");
            }
        }

        log.info("bulkSeed: added={} skipped={} notFound={}", added.size(), skipped.size(), notFound.size());
        return Map.of(
                "added",    added,
                "skipped",  skipped,
                "notFound", notFound,
                "addedCount",    added.size(),
                "skippedCount",  skipped.size(),
                "notFoundCount", notFound.size()
        );
    }

    /**
     * Autocomplete against the loaded scrip master. Matches EITHER token or
     * substring of symbol/name. Case-insensitive. Cap results at {@code LOOKUP_LIMIT}.
     */
    @GetMapping("/lookup")
    @ResponseBody
    public List<Map<String, String>> lookup(@RequestParam("q") String query) {
        if (query == null || query.isBlank()) return List.of();

        // Fast path — exact token match first (very common)
        try {
            var byToken = masterService.findByToken(query.trim());
            if (byToken.isPresent()) {
                return List.of(row(byToken.get()));
            }
        } catch (Exception ignored) {}

        // Slow path — substring scan on symbol / name. Uses a small helper below.
        String q = query.trim().toLowerCase();
        return masterService.searchByNameOrSymbol(q, LOOKUP_LIMIT).stream()
                .map(InstrumentAdminController::row)
                .toList();
    }

    private static Map<String, String> row(Instrument i) {
        Map<String, String> m = new HashMap<>();
        m.put("token",    i.token());
        m.put("symbol",   i.symbol());
        m.put("name",     i.name());
        m.put("exchange", i.exchSeg() == null ? "" : i.exchSeg());
        m.put("expiry",   i.expiry());
        return m;
    }
}
