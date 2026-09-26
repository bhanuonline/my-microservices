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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
