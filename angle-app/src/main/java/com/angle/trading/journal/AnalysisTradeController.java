package com.angle.trading.journal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST + page endpoints for the Analysis Trade Journal.
 *
 *   GET  /live/trades                     → UI page
 *   GET  /api/analysis/trades             → list all trades
 *   POST /api/analysis/trades             → save trade from suggestion
 *   GET  /api/analysis/trades/stats       → win rate, P&L stats
 *   PUT  /api/analysis/trades/{id}/close  → manual close
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalysisTradeController {

    private final AnalysisTradeService tradeService;

    // ── UI page ───────────────────────────────────────────────────

    @GetMapping("/live/trades")
    public String tradesPage(Model model) {
        return "live/trades";
    }

    // ── REST API ──────────────────────────────────────────────────

    @GetMapping("/api/analysis/trades")
    @ResponseBody
    public List<TradeJournalDto> list(
            @RequestParam(required = false) String status) {
        if ("OPEN".equalsIgnoreCase(status)) return tradeService.findOpen();
        return tradeService.findAll();
    }

    @GetMapping("/api/analysis/trades/{id}")
    @ResponseBody
    public TradeJournalDto get(@PathVariable Long id) {
        return tradeService.findById(id);
    }

    @PostMapping("/api/analysis/trades")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody SaveTradeRequestDto req) {
        try {
            TradeJournalDto saved = tradeService.save(req);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/analysis/trades/{id}/close")
    @ResponseBody
    public ResponseEntity<?> close(@PathVariable Long id,
                                   @RequestParam(required = false) BigDecimal closePrice,
                                   @RequestParam(required = false) String notes) {
        try {
            TradeJournalDto closed = tradeService.manualClose(id, closePrice, notes);
            return ResponseEntity.ok(closed);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/analysis/trades/stats")
    @ResponseBody
    public TradeStatsDto stats() {
        return tradeService.stats();
    }
}
