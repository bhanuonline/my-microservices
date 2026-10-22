package com.angle.trading.journal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/journal")
@RequiredArgsConstructor
public class TradeJournalController {

    private final TradeJournalRepository repo;

    // ── Dashboard / list ────────────────────────────────────────────────

    @GetMapping
    public String list(@RequestParam(required = false) String outcome,
                       @RequestParam(required = false) String segment,
                       @RequestParam(required = false) String q,
                       Model model) {

        List<TradeJournalEntity> trades;
        if (q != null && !q.isBlank()) {
            trades = repo.searchBySymbol(q.trim());
        } else if (outcome != null && !outcome.isBlank()) {
            trades = repo.findByOutcomeOrderByEntryDateTimeDesc(outcome);
        } else if (segment != null && !segment.isBlank()) {
            trades = repo.findBySegmentOrderByEntryDateTimeDesc(segment);
        } else {
            trades = repo.findAllByOrderByEntryDateTimeDesc();
        }

        long total    = repo.count();
        long wins     = repo.countByOutcome("WIN");
        long losses   = repo.countByOutcome("LOSS");
        long opens    = repo.countByOutcome("OPEN");
        long winRate  = (wins + losses) == 0 ? 0 : Math.round(100.0 * wins / (wins + losses));
        BigDecimal totalPnl = repo.totalPnl();

        model.addAttribute("trades",    trades);
        model.addAttribute("total",     total);
        model.addAttribute("wins",      wins);
        model.addAttribute("losses",    losses);
        model.addAttribute("opens",     opens);
        model.addAttribute("winRate",   winRate);
        model.addAttribute("totalPnl",  totalPnl.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("filterOutcome", outcome);
        model.addAttribute("filterSegment", segment);
        model.addAttribute("filterQ",   q);
        return "journal/list";
    }

    // ── New entry form ───────────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("trade",   new TradeJournalEntity());
        model.addAttribute("editing", false);
        addFormOptions(model);
        return "journal/form";
    }

    // ── Save new entry ───────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(@ModelAttribute TradeJournalEntity trade,
                       @RequestParam(name = "indicators", required = false) String[] indicators,
                       RedirectAttributes ra) {

        trade.setIndicatorsUsed(indicators != null ? String.join(",", indicators) : "");
        trade.setCreatedAt(Instant.now());
        trade.setUpdatedAt(Instant.now());
        computeOutcome(trade);
        repo.save(trade);
        ra.addFlashAttribute("flash", "Trade saved successfully.");
        return "redirect:/journal/" + trade.getId();
    }

    // ── Detail view ──────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        TradeJournalEntity trade = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));
        model.addAttribute("trade", trade);
        model.addAttribute("indicatorList",
                trade.getIndicatorsUsed() != null && !trade.getIndicatorsUsed().isBlank()
                        ? Arrays.asList(trade.getIndicatorsUsed().split(","))
                        : List.of());
        return "journal/detail";
    }

    // ── Edit form ────────────────────────────────────────────────────────

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        TradeJournalEntity trade = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));
        model.addAttribute("trade",   trade);
        model.addAttribute("editing", true);
        model.addAttribute("selectedIndicators",
                trade.getIndicatorsUsed() != null
                        ? Arrays.asList(trade.getIndicatorsUsed().split(","))
                        : List.of());
        addFormOptions(model);
        return "journal/form";
    }

    // ── Update existing ──────────────────────────────────────────────────

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @ModelAttribute TradeJournalEntity form,
                         @RequestParam(name = "indicators", required = false) String[] indicators,
                         RedirectAttributes ra) {

        TradeJournalEntity existing = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));

        existing.setSymbol(form.getSymbol());
        existing.setSegment(form.getSegment());
        existing.setTradeType(form.getTradeType());
        existing.setEntryPrice(form.getEntryPrice());
        existing.setEntryDateTime(form.getEntryDateTime());
        existing.setExitPrice(form.getExitPrice());
        existing.setExitDateTime(form.getExitDateTime());
        existing.setStopLoss(form.getStopLoss());
        existing.setTarget(form.getTarget());
        existing.setQuantity(form.getQuantity());
        existing.setLotSize(form.getLotSize());
        existing.setTimeframe(form.getTimeframe());
        existing.setIndicatorsUsed(indicators != null ? String.join(",", indicators) : "");
        existing.setStrategy(form.getStrategy());
        existing.setReasonForEntry(form.getReasonForEntry());
        existing.setMarketCondition(form.getMarketCondition());
        existing.setEmotionalState(form.getEmotionalState());
        existing.setNotes(form.getNotes());
        existing.setUpdatedAt(Instant.now());
        computeOutcome(existing);
        repo.save(existing);

        ra.addFlashAttribute("flash", "Trade updated.");
        return "redirect:/journal/" + id;
    }

    // ── Delete ───────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.deleteById(id);
        ra.addFlashAttribute("flash", "Trade deleted.");
        return "redirect:/journal";
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void computeOutcome(TradeJournalEntity t) {
        if (t.getExitPrice() == null || t.getQuantity() == null) {
            t.setOutcome("OPEN");
            t.setPnl(null);
            t.setPnlPercent(null);
            return;
        }
        BigDecimal diff = "SELL".equals(t.getTradeType())
                ? t.getEntryPrice().subtract(t.getExitPrice())
                : t.getExitPrice().subtract(t.getEntryPrice());

        BigDecimal pnl = diff.multiply(BigDecimal.valueOf(t.getQuantity()));
        t.setPnl(pnl.setScale(2, RoundingMode.HALF_UP));

        BigDecimal pct = diff.divide(t.getEntryPrice(), 6, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(100))
                             .setScale(2, RoundingMode.HALF_UP);
        t.setPnlPercent(pct);

        int cmp = pnl.compareTo(BigDecimal.ZERO);
        t.setOutcome(cmp > 0 ? "WIN" : cmp < 0 ? "LOSS" : "BREAKEVEN");
    }

    private void addFormOptions(Model model) {
        model.addAttribute("segments",    List.of("NSE", "BSE", "MCX", "NFO", "BFO", "CDS"));
        model.addAttribute("timeframes",  List.of("1m", "3m", "5m", "15m", "30m", "1h", "1d"));
        model.addAttribute("conditions",  List.of("Trending", "Sideways", "Volatile", "Choppy"));
        model.addAttribute("emotions",    List.of("Calm", "Confident", "Anxious", "FOMO", "Greedy", "Fearful", "Neutral"));
        model.addAttribute("allIndicators",
                List.of("RSI", "MACD", "ADX", "+DI/-DI", "EMA", "SMA", "VWAP",
                        "Bollinger Bands", "Supertrend", "Stochastic", "ATR", "ORB", "OB/FVG"));
    }
}
