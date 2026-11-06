package com.angle.trading.signal;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/signal-calculator")
@RequiredArgsConstructor
@Slf4j
public class SignalRecordController {

    private static final int PAGE_SIZE = 10;

    private static final List<String> MARKETS = List.of("NSE", "MCX", "BSE", "NFO", "BFO", "CDS");

    private final SignalRecordRepository repo;

    // ── Calculator page ──────────────────────────────────────────────────────

    @GetMapping
    public String calculatorPage(@RequestParam(required = false) Long load, Model model) {
        model.addAttribute("markets", MARKETS);
        model.addAttribute("today", LocalDate.now().toString());

        if (load != null) {
            repo.findById(load).ifPresent(r -> {
                model.addAttribute("editId",    r.getId());
                model.addAttribute("market",    r.getMarket());
                model.addAttribute("symbol",    r.getSymbol());
                model.addAttribute("tradeDate", r.getTradeDate().toString());
                model.addAttribute("note",      r.getNote() != null ? r.getNote() : "");
                model.addAttribute("savedJson", r.getStateJson());
            });
        }

        // Defaults if not loading a record
        if (!model.containsAttribute("editId")) {
            model.addAttribute("editId",    null);
            model.addAttribute("market",    "NSE");
            model.addAttribute("symbol",    "");
            model.addAttribute("tradeDate", LocalDate.now().toString());
            model.addAttribute("note",      "");
            model.addAttribute("savedJson", null);
        }

        return "signals/calculator";
    }

    // ── History page ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    public String historyPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        PageRequest pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "tradeDate", "createdAt"));

        Page<SignalRecordEntity> result = repo.findAll(filters(market, symbol, from, to), pageable);

        model.addAttribute("records",     result.getContent());
        model.addAttribute("totalPages",  result.getTotalPages());
        model.addAttribute("totalItems",  result.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("markets",     MARKETS);

        // Echo filters back for the form
        model.addAttribute("filterMarket", market != null ? market : "");
        model.addAttribute("filterSymbol", symbol != null ? symbol : "");
        model.addAttribute("filterFrom",   from   != null ? from.toString() : "");
        model.addAttribute("filterTo",     to     != null ? to.toString()   : "");

        return "signals/history";
    }

    // ── REST: Create ─────────────────────────────────────────────────────────

    @PostMapping("/records")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(@RequestBody SignalRecordRequest req) {
        SignalRecordEntity e = new SignalRecordEntity();
        applyRequest(e, req);
        e.setCreatedAt(Instant.now());
        repo.save(e);
        log.debug("Signal record created id={} market={} symbol={}", e.getId(), e.getMarket(), e.getSymbol());
        return ResponseEntity.ok(Map.of("id", e.getId(), "status", "created"));
    }

    // ── REST: Update ─────────────────────────────────────────────────────────

    @PutMapping("/records/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long id,
            @RequestBody SignalRecordRequest req) {

        return repo.findById(id).map(e -> {
            applyRequest(e, req);
            repo.save(e);
            log.debug("Signal record updated id={}", id);
            return ResponseEntity.ok(Map.of("status", "updated"));
        }).orElse(ResponseEntity.notFound().<Map<String, String>>build());
    }

    // ── REST: Delete ─────────────────────────────────────────────────────────

    @DeleteMapping("/records/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        log.debug("Signal record deleted id={}", id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyRequest(SignalRecordEntity e, SignalRecordRequest req) {
        e.setMarket(req.market());
        e.setSymbol(req.symbol());
        e.setTradeDate(LocalDate.parse(req.tradeDate()));
        e.setNote(req.note());
        e.setOverallSignal(req.overallSignal());
        e.setOverallSum(req.overallSum());
        e.setStateJson(req.stateJson());
        e.setUpdatedAt(Instant.now());
        if (e.getCreatedAt() == null) e.setCreatedAt(Instant.now());
    }

    private static Specification<SignalRecordEntity> filters(
            String market, String symbol, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (market != null && !market.isBlank())
                predicates.add(cb.equal(root.get("market"), market));
            if (symbol != null && !symbol.isBlank())
                predicates.add(cb.like(cb.lower(root.get("symbol")),
                        "%" + symbol.toLowerCase() + "%"));
            if (from != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("tradeDate"), from));
            if (to != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("tradeDate"), to));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
