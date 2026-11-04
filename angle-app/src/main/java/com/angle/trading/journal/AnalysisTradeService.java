package com.angle.trading.journal;

import com.angle.trading.dto.IndicatorsDto;
import com.angle.trading.dto.MtfConclusionDto;
import com.angle.trading.dto.TradeSuggestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages the analysis trade journal — saving suggestions, closing trades, computing stats.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTradeService {

    private final AnalysisTradeJournalRepository repo;

    // ── Save trade ────────────────────────────────────────────────

    /**
     * Saves a trade from an analysis suggestion.
     * Extracts all relevant context from the full analysis response.
     */
    @Transactional
    public TradeJournalDto save(SaveTradeRequestDto req) {
        var analysis = req.analysis();
        if (analysis == null) throw new IllegalArgumentException("Analysis response is required.");

        TradeSuggestionDto trade = analysis.tradeSuggestion();
        MtfConclusionDto   conc  = analysis.conclusion();

        if (trade == null || "NO_TRADE".equals(trade.direction())) {
            throw new IllegalArgumentException("No trade suggestion available — direction is NO_TRADE.");
        }

        var e = new AnalysisTradeJournalEntity();
        e.setSymbol(req.symbol());
        e.setExchange(req.exchange());
        e.setIntervalStack(req.intervalStack());
        e.setDirection(trade.direction());
        e.setEntryPrice(req.entryPrice());
        e.setEntryTime(Instant.now());

        if (trade.entryZone() != null) {
            e.setEntryZoneFrom(trade.entryZone().from());
            e.setEntryZoneTo(trade.entryZone().to());
        }
        if (trade.stopLoss() != null) {
            e.setStopLoss(trade.stopLoss().price());
            e.setStopBasis(trade.stopLoss().basis());
        }
        if (trade.target1() != null) {
            e.setTarget1(trade.target1().price());
            e.setTarget1Rr(trade.target1().riskReward());
        }
        if (trade.target2() != null) {
            e.setTarget2(trade.target2().price());
            e.setTarget2Rr(trade.target2().riskReward());
        }

        // MTF context
        if (conc != null) {
            e.setPrimaryBias(conc.primaryBias());
            e.setConfidence(conc.confidence());
            e.setTradeSetup(conc.tradeSetup());
            e.setAlignedTfs(String.join(",", conc.alignedTimeframes() != null ? conc.alignedTimeframes() : List.of()));
            e.setConflictingTfs(String.join(",", conc.conflictingTimeframes() != null ? conc.conflictingTimeframes() : List.of()));
        }

        // Indicators from highest TF
        if (analysis.multiTimeframe() != null && !analysis.multiTimeframe().isEmpty()) {
            var tfs    = analysis.multiTimeframe();
            var tfKeys = tfs.keySet().stream().toList();
            IndicatorsDto top = tfs.get(tfKeys.get(tfKeys.size() - 1));
            if (top != null) {
                if (top.momentum() != null) e.setRsi14(top.momentum().rsi14());
                if (top.trend() != null) {
                    e.setAdx14(top.trend().adx14());
                    e.setPlusDi(top.trend().plusDi());
                    e.setMinusDi(top.trend().minusDi());
                    if (top.trend().supertrend() != null)
                        e.setSupertrend(Boolean.TRUE.equals(top.trend().supertrend().bullish()) ? "BULLISH" : "BEARISH");
                }
            }
        }

        // ORB
        if (analysis.orb() != null && analysis.orb().timeframes() != null) {
            analysis.orb().timeframes().forEach(tf -> {
                switch (tf.minutes()) {
                    case 15 -> e.setOrb15m(tf.signal());
                    case 30 -> e.setOrb30m(tf.signal());
                    case 60 -> e.setOrb60m(tf.signal());
                }
            });
        }

        var saved = repo.save(e);
        log.info("[TradeJournal] Saved {} {} at ₹{} — stop ₹{} T1 ₹{}",
                saved.getDirection(), saved.getSymbol(), saved.getEntryPrice(),
                saved.getStopLoss(), saved.getTarget1());
        return toDto(saved);
    }

    // ── Close trade ───────────────────────────────────────────────

    /** Auto-close a trade when price hits target or stop. */
    @Transactional
    public void autoClose(Long id, BigDecimal closePrice, String reason) {
        repo.findById(id).ifPresent(t -> {
            if (!"OPEN".equals(t.getStatus())) return;
            t.setStatus(reason);
            t.setClosePrice(closePrice);
            t.setCloseTime(Instant.now());
            t.setCloseReason(reason);
            BigDecimal points = computePoints(t.getDirection(), t.getEntryPrice(), closePrice);
            t.setProfitPoints(points);
            if (t.getEntryPrice() != null && t.getEntryPrice().compareTo(BigDecimal.ZERO) != 0)
                t.setProfitPercent(points.divide(t.getEntryPrice(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)));
            repo.save(t);
            log.info("[TradeJournal] Auto-closed {} {} → {} at ₹{} P&L={} pts",
                    t.getSymbol(), t.getDirection(), reason, closePrice, points);
        });
    }

    /** Manual close from UI. */
    @Transactional
    public TradeJournalDto manualClose(Long id, BigDecimal closePrice, String notes) {
        var t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));
        if (!"OPEN".equals(t.getStatus())) throw new IllegalArgumentException("Trade already closed.");
        t.setStatus("MANUAL_CLOSE");
        t.setClosePrice(closePrice);
        t.setCloseTime(Instant.now());
        t.setCloseReason("MANUAL");
        if (notes != null) t.setNotes(notes);
        BigDecimal points = computePoints(t.getDirection(), t.getEntryPrice(), closePrice);
        t.setProfitPoints(points);
        if (t.getEntryPrice() != null && t.getEntryPrice().compareTo(BigDecimal.ZERO) != 0)
            t.setProfitPercent(points.divide(t.getEntryPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        return toDto(repo.save(t));
    }

    // ── Queries ───────────────────────────────────────────────────

    public List<TradeJournalDto> findAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    public List<TradeJournalDto> findOpen() {
        return repo.findByStatus("OPEN").stream().map(this::toDto).toList();
    }

    public TradeJournalDto findById(Long id) {
        return repo.findById(id).map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + id));
    }

    /** All open entities — used by the monitor for price checking. */
    List<AnalysisTradeJournalEntity> findOpenEntities() {
        return repo.findByStatus("OPEN");
    }

    // ── Stats ─────────────────────────────────────────────────────

    public TradeStatsDto stats() {
        List<AnalysisTradeJournalEntity> all = repo.findAll();
        long total    = all.size();
        long open     = all.stream().filter(t -> "OPEN".equals(t.getStatus())).count();
        long closed   = total - open;
        long wins     = all.stream().filter(t -> t.getStatus() != null &&
                (t.getStatus().contains("TARGET"))).count();
        long losses   = all.stream().filter(t -> "STOP_HIT".equals(t.getStatus())).count();
        long t1hits   = all.stream().filter(t -> "TARGET1_HIT".equals(t.getStatus())).count();
        long t2hits   = all.stream().filter(t -> "TARGET2_HIT".equals(t.getStatus())).count();
        long stopHits = losses;
        long manual   = all.stream().filter(t -> "MANUAL_CLOSE".equals(t.getStatus())).count();

        BigDecimal totalPoints = all.stream()
                .filter(t -> t.getProfitPoints() != null)
                .map(AnalysisTradeJournalEntity::getProfitPoints)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String winRate = closed > 0
                ? String.format("%.1f%%", (wins * 100.0 / closed))
                : "—";

        return new TradeStatsDto(total, open, closed, wins, losses,
                winRate, totalPoints, t1hits, t2hits, stopHits, manual);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private BigDecimal computePoints(String direction, BigDecimal entry, BigDecimal close) {
        if (entry == null || close == null) return BigDecimal.ZERO;
        BigDecimal diff = close.subtract(entry);
        return "SHORT".equals(direction) ? diff.negate().setScale(2, RoundingMode.HALF_UP)
                                         : diff.setScale(2, RoundingMode.HALF_UP);
    }

    private TradeJournalDto toDto(AnalysisTradeJournalEntity e) {
        return new TradeJournalDto(e.getId(), e.getSymbol(), e.getExchange(), e.getDirection(),
                e.getEntryPrice(), e.getEntryTime(), e.getEntryZoneFrom(), e.getEntryZoneTo(),
                e.getStopLoss(), e.getTarget1(), e.getTarget1Rr(), e.getTarget2(), e.getTarget2Rr(),
                e.getStatus(), e.getClosePrice(), e.getCloseTime(), e.getCloseReason(),
                e.getProfitPoints(), e.getProfitPercent(),
                e.getPrimaryBias(), e.getConfidence(), e.getTradeSetup(), e.getIntervalStack(),
                e.getRsi14(), e.getAdx14(), e.getSupertrend(),
                e.getOrb15m(), e.getOrb30m(), e.getOrb60m(), e.getNotes(), e.getCreatedAt());
    }
}
