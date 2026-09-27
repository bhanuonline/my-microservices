package com.angle.trading.analysis;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.StrategyRegistry;
import com.angle.trading.strategy.model.IntentAction;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a strategy across a candle series and converts every non-HOLD intent
 * into a chart marker (frontend renders arrows below/above the candle).
 *
 * Marker shape (TradingView Lightweight Charts format):
 *   {
 *     "time":     1737891360,
 *     "position": "belowBar" | "aboveBar",
 *     "color":    "#31D39B" | "#FF6B70",
 *     "shape":    "arrowUp" | "arrowDown" | "circle",
 *     "text":     "LONG"    | "SHORT"    | "EXIT",
 *     "id":       "150"                            (candle index — used for tooltip)
 *   }
 *
 * Extra {@code detail} field carries entry/stop/target/rationale — the
 * frontend shows it on click.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalMarkerService {

    private static final MathContext MC = MathContext.DECIMAL64;

    /** Strategies included in consensus voting. Ensemble excluded — it's itself a vote. */
    private static final Set<String> CONSENSUS_MEMBERS = Set.of(
            "moving-average-crossover",
            "rsi-mean-reversion",
            "macd-crossover",
            "ob-retest",
            "sweep-fvg",
            "volume-breakout",
            "bollinger-bounce"
    );

    private final StrategyRegistry strategyRegistry;

    public List<Map<String, Object>> markers(String strategyName, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return List.of();
        Strategy strategy;
        try {
            strategy = strategyRegistry.get(strategyName);
        } catch (Exception e) {
            log.warn("Unknown strategy '{}' — no markers", strategyName);
            return List.of();
        }

        List<TradeIntent> intents;
        try {
            intents = strategy.evaluate(candles);
        } catch (Exception e) {
            log.warn("Strategy {} threw while evaluating: {}", strategyName, e.getMessage());
            return List.of();
        }
        if (intents == null || intents.size() != candles.size()) return List.of();

        List<Map<String, Object>> markers = new ArrayList<>();
        for (int i = 0; i < intents.size(); i++) {
            TradeIntent it = intents.get(i);
            if (it == null || it.action() == null || it.action() == IntentAction.HOLD) continue;
            markers.add(toMarker(i, candles.get(i), it, strategyName));
        }
        return markers;
    }

    /**
     * Consensus markers — only emit when at least {@code minAgreement} of the
     * child strategies (excluding ensemble) fire the SAME direction on the same
     * candle. Filters out false signals from any single noisy strategy.
     *
     * Marker text: "3× LONG" (number of agreeing strategies).
     * Marker detail includes the list of agreeing strategy names + averaged
     * entry / widest stop / nearest target across all agreeing intents.
     */
    public List<Map<String, Object>> consensusMarkers(int minAgreement, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return List.of();

        // Run every member strategy once, keep intent list per strategy.
        Map<String, List<TradeIntent>> perStrategy = new LinkedHashMap<>();
        for (String name : CONSENSUS_MEMBERS) {
            try {
                Strategy s = strategyRegistry.get(name);
                List<TradeIntent> intents = s.evaluate(candles);
                if (intents != null && intents.size() == candles.size()) {
                    perStrategy.put(name, intents);
                }
            } catch (Exception e) {
                log.debug("Consensus: strategy {} skipped: {}", name, e.getMessage());
            }
        }

        List<Map<String, Object>> markers = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            List<Vote> longs  = new ArrayList<>();
            List<Vote> shorts = new ArrayList<>();
            for (var e : perStrategy.entrySet()) {
                TradeIntent it = e.getValue().get(i);
                if (it == null || it.action() == null) continue;
                if (it.action() == IntentAction.ENTER_LONG)  longs.add(new Vote(e.getKey(), it));
                if (it.action() == IntentAction.ENTER_SHORT) shorts.add(new Vote(e.getKey(), it));
            }
            if (longs.size() >= minAgreement) {
                markers.add(consensusMarker(i, candles.get(i), IntentAction.ENTER_LONG, longs));
            } else if (shorts.size() >= minAgreement) {
                markers.add(consensusMarker(i, candles.get(i), IntentAction.ENTER_SHORT, shorts));
            }
        }
        return markers;
    }

    private static Map<String, Object> consensusMarker(int i, Candle c, IntentAction action, List<Vote> votes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", c.timestamp().getEpochSecond());
        m.put("id",   String.valueOf(i));
        if (action == IntentAction.ENTER_LONG) {
            m.put("position", "belowBar");
            m.put("color",    "#31D39B");
            m.put("shape",    "arrowUp");
            m.put("text",     votes.size() + "× LONG");
        } else {
            m.put("position", "aboveBar");
            m.put("color",    "#FF6B70");
            m.put("shape",    "arrowDown");
            m.put("text",     votes.size() + "× SHORT");
        }

        // Aggregate levels: averaged entry, widest stop, nearest target.
        BigDecimal entrySum = BigDecimal.ZERO;
        int entryCount = 0;
        BigDecimal widestStop = null;
        BigDecimal nearestTarget = null;
        List<String> agreedNames = new ArrayList<>();
        List<String> rationales  = new ArrayList<>();
        for (Vote v : votes) {
            agreedNames.add(v.strategy);
            if (v.intent.entry()  != null) { entrySum = entrySum.add(v.intent.entry()); entryCount++; }
            if (v.intent.stop()   != null) {
                if (action == IntentAction.ENTER_LONG) {
                    widestStop = (widestStop == null || v.intent.stop().compareTo(widestStop) < 0) ? v.intent.stop() : widestStop;
                } else {
                    widestStop = (widestStop == null || v.intent.stop().compareTo(widestStop) > 0) ? v.intent.stop() : widestStop;
                }
            }
            if (v.intent.target() != null) {
                if (action == IntentAction.ENTER_LONG) {
                    nearestTarget = (nearestTarget == null || v.intent.target().compareTo(nearestTarget) < 0) ? v.intent.target() : nearestTarget;
                } else {
                    nearestTarget = (nearestTarget == null || v.intent.target().compareTo(nearestTarget) > 0) ? v.intent.target() : nearestTarget;
                }
            }
            if (v.intent.rationale() != null) rationales.add(v.strategy + ": " + v.intent.rationale());
        }
        BigDecimal avgEntry = entryCount > 0
                ? entrySum.divide(BigDecimal.valueOf(entryCount), 2, RoundingMode.HALF_UP)
                : c.close();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("strategy",     "consensus (" + votes.size() + " agree)");
        detail.put("action",       action.name());
        detail.put("agreedStrategies", agreedNames);
        detail.put("entry",  avgEntry);
        if (widestStop    != null) detail.put("stop",   widestStop);
        if (nearestTarget != null) detail.put("target", nearestTarget);
        detail.put("rationale", String.join(" | ", rationales));
        m.put("detail", detail);
        return m;
    }

    /** One agreeing vote in a consensus round. */
    private record Vote(String strategy, TradeIntent intent) {}

    private static Map<String, Object> toMarker(int i, Candle c, TradeIntent it, String strategyName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", c.timestamp().getEpochSecond());

        switch (it.action()) {
            case ENTER_LONG -> {
                m.put("position", "belowBar");
                m.put("color",    "#31D39B");
                m.put("shape",    "arrowUp");
                m.put("text",     "LONG");
            }
            case ENTER_SHORT -> {
                m.put("position", "aboveBar");
                m.put("color",    "#FF6B70");
                m.put("shape",    "arrowDown");
                m.put("text",     "SHORT");
            }
            case EXIT -> {
                m.put("position", "aboveBar");
                m.put("color",    "#F0A63E");
                m.put("shape",    "circle");
                m.put("text",     "EXIT");
            }
            case HOLD -> { return m; }   // unreachable — filtered above
        }
        m.put("id", String.valueOf(i));

        // Detail for click-to-inspect popover
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("strategy", strategyName);
        detail.put("action",   it.action().name());
        if (it.entry()  != null) detail.put("entry",  it.entry());
        if (it.stop()   != null) detail.put("stop",   it.stop());
        if (it.target() != null) detail.put("target", it.target());
        if (it.rationale() != null) detail.put("rationale", it.rationale());
        m.put("detail", detail);
        return m;
    }
}
