package com.angle.trading.controller;

import com.angle.trading.ai.agent.ResearchReport;
import com.angle.trading.ai.agent.SymbolResearchAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET  /api/research?symbol=HDFCBANK-EQ  → ResearchReport JSON
 * POST /api/research/cache/clear          → clear in-memory cache
 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class SymbolResearchController {

    private final SymbolResearchAgent agent;

    @GetMapping
    public ResponseEntity<ResearchReport> research(@RequestParam String symbol) {
        if (symbol == null || symbol.isBlank())
            return ResponseEntity.badRequest().build();
        ResearchReport report = agent.research(symbol.trim().toUpperCase());
        return ResponseEntity.ok(report);
    }

    @PostMapping("/cache/clear")
    public Map<String, Object> clearCache() {
        agent.clearCache();
        return Map.of("cleared", true);
    }

    @GetMapping("/cache/stats")
    public Map<String, Object> cacheStats() {
        return agent.cacheStats();
    }
}
