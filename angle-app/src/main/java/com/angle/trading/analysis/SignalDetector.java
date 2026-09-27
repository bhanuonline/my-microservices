package com.angle.trading.analysis;

import com.angle.trading.ai.AiSignalService;
import com.angle.trading.ai.model.AiSignal;
import com.angle.trading.bias.BiasSheetService;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.broker.angel.stream.CandleClosedEvent;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.config.SignalsProperties;
import com.angle.trading.marketdata.InstrumentNameResolver;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.SignalEntity;
import com.angle.trading.service.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Listens for {@link CandleClosedEvent}. On each qualifying close:
 *   1. Fetch last ~200 candles via MarketDataService (cached, fast).
 *   2. Ask SignalMarkerService for consensus markers on the newest bar only.
 *   3. Dedupe (skip if same symbol/action fired recently).
 *   4. Save as SignalEntity(OPEN).
 *
 * The MonitorService (LiveSignalMonitor) picks it up from there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignalDetector {

    /** How many candles to feed the strategies for context. */
    private static final int LOOKBACK_CANDLES = 250;

    private final SignalsProperties props;
    private final SignalMarkerService markerService;
    private final MarketDataService marketDataService;
    private final SignalService signalService;
    private final InstrumentNameResolver nameResolver;
    private final AiSignalService aiSignalService;
    private final BiasSheetService biasSheetService;
    private final RegimeService regimeService;
    private final MtfConfirmationService mtfService;
    private final com.angle.trading.news.NewsBlackoutService newsBlackoutService;
    private final PipelineStats pipelineStats;

    private volatile Set<Interval> enabledIntervals;

    @EventListener
    public void onCandleClosed(CandleClosedEvent e) {
        if (!props.getDetector().isEnabled()) return;
        if (!intervals().contains(e.interval())) return;

        String token = e.symbolToken();
        Interval iv  = e.interval();

        // Fetch context: last N candles for this instrument+interval
        Exchange exch = angelExchangeCodeToEnum(e.exchangeCode());
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(estimateLookbackDays(iv, LOOKBACK_CANDLES));
        List<Candle> all;
        try {
            all = marketDataService.getCandles("ANGEL", exch, token, iv, from, to);
        } catch (Exception ex) {
            log.debug("SignalDetector: candle fetch failed for {}:{}: {}", token, iv, ex.getMessage());
            return;
        }
        if (all == null || all.isEmpty()) return;

        int start = Math.max(0, all.size() - LOOKBACK_CANDLES);
        List<Candle> tail = all.subList(start, all.size());
        int lastIdx = tail.size() - 1;

        pipelineStats.incAttempted();

        // Regime + time + VIX gate. If any is disabled it always passes.
        RegimeService.Decision gate = regimeService.allow(token, exch, iv, tail);
        if (!gate.allowed()) {
            pipelineStats.incRegime();
            log.debug("SignalDetector: {} filtered — {}", token, gate.reason());
            return;
        }

        // Run consensus — only care about the LAST candle (the one that just closed)
        List<Map<String, Object>> markers = markerService.consensusMarkers(
                props.getDetector().getMinAgreement(), tail);
        Map<String, Object> lastMarker = markers.stream()
                .filter(m -> m.get("id") != null && String.valueOf(lastIdx).equals(m.get("id").toString()))
                .findFirst()
                .orElse(null);
        if (lastMarker == null) return;

        // Extract fields
        Map<String, Object> detail = safeMap(lastMarker.get("detail"));
        String action = "LONG".equalsIgnoreCase(strFromDetail(detail, "action").replace("ENTER_", ""))
                ? "BUY" : "SELL";
        if ("ENTER_SHORT".equalsIgnoreCase(strFromDetail(detail, "action"))) action = "SELL";
        if ("ENTER_LONG".equalsIgnoreCase(strFromDetail(detail, "action")))  action = "BUY";

        // Dedupe
        if (signalService.hasRecentSignal(token, action, props.getDetector().getDedupeMinutes())) {
            pipelineStats.incDedupe();
            log.debug("SignalDetector: dedupe skip {} {}", token, action);
            return;
        }

        // Multi-timeframe confirmation — do higher TFs agree on direction?
        MtfConfirmationService.Decision mtf = mtfService.confirm(token, exch, iv, action);
        if (!mtf.allowed()) {
            pipelineStats.incMtf();
            log.info("SignalDetector: {} {} MTF DENY — {}", token, action, mtf.reason());
            return;
        }

        // News blackout — inside window around a scheduled market event?
        var news = newsBlackoutService.check(token);
        if (!news.allowed()) {
            pipelineStats.incNews();
            log.info("SignalDetector: {} {} NEWS BLACKOUT — {}", token, action, news.reason());
            return;
        }

        pipelineStats.incPassed();

        SignalEntity s = new SignalEntity();
        s.setSymbolToken(token);
        s.setSymbol(nameResolver.resolve(token));
        s.setExchange(exch.name());
        s.setIntervalType(iv.name());
        s.setAction(action);
        s.setEntry(toBd(detail.get("entry"),  tail.get(lastIdx).close()));
        s.setStop(toBd(detail.get("stop"),   tail.get(lastIdx).close()));
        s.setTarget(toBd(detail.get("target"), tail.get(lastIdx).close()));
        Object agreed = detail.get("agreedStrategies");
        if (agreed instanceof List<?> list) {
            s.setAgreedStrategies(String.join(",", list.stream().map(Object::toString).toList()));
            s.setAgreedCount(list.size());
        }
        s.setStatus("OPEN");
        s.setNotes(strFromDetail(detail, "rationale"));

        SignalEntity saved = signalService.save(s);
        log.info("SignalDetector: NEW {} {} @ {} · stop {} · target {} · agreed by {}",
                action, s.getSymbol(), s.getEntry(), s.getStop(), s.getTarget(), s.getAgreedCount());

        // Fire AI-confirmer async so the tick pipeline isn't blocked on Claude.
        if (props.getDetector().isAiConfirm() && aiSignalService.isEnabled()) {
            askAiAsync(saved);
        }
    }

    /**
     * Runs the bias sheet + AI call off-thread. Result stored as a separate row
     * with source=AI so it shows on /signals alongside the consensus signal.
     * Failures are logged, never rethrown — a broken AI must NOT block trading signals.
     */
    @Async
    void askAiAsync(SignalEntity consensus) {
        try {
            BiasProperties.Instrument cfg = new BiasProperties.Instrument();
            cfg.setSymbol(consensus.getSymbol());
            cfg.setSymbolToken(consensus.getSymbolToken());
            try { cfg.setExchange(Exchange.valueOf(consensus.getExchange())); }
            catch (Exception ignore) { cfg.setExchange(Exchange.NSE); }
            try { cfg.setIntradayInterval(Interval.valueOf(consensus.getIntervalType())); }
            catch (Exception ignore) { /* leave default */ }

            BiasSheet sheet = biasSheetService.build(cfg);
            AiSignal ai = aiSignalService.analyse(sheet);
            if (ai == null || "WAIT".equalsIgnoreCase(ai.action())
                    || "AVOID".equalsIgnoreCase(ai.action())) {
                log.info("AI-confirmer: {} WAIT/AVOID for {} — no signal saved", ai == null ? "null" : ai.action(),
                        consensus.getSymbol());
                return;
            }

            String aiAction = "LONG".equalsIgnoreCase(ai.action()) ? "BUY"
                            : "SHORT".equalsIgnoreCase(ai.action()) ? "SELL"
                            : ai.action().toUpperCase();

            SignalEntity aiSig = new SignalEntity();
            aiSig.setSymbolToken(consensus.getSymbolToken());
            aiSig.setSymbol(consensus.getSymbol());
            aiSig.setExchange(consensus.getExchange());
            aiSig.setIntervalType(consensus.getIntervalType());
            aiSig.setAction(aiAction);
            aiSig.setEntry(consensus.getEntry());
            aiSig.setStop(consensus.getStop());
            aiSig.setTarget(consensus.getTarget());
            aiSig.setAgreedStrategies("claude/" + ai.model());
            aiSig.setAgreedCount(0);
            aiSig.setStatus("OPEN");
            aiSig.setSource("AI");
            aiSig.setAiConfidence(ai.confidence());
            aiSig.setAiRationale(clip(ai.rationale(), 1450));
            signalService.save(aiSig);
            log.info("AI-confirmer: {} {} for {} — {} confidence",
                    aiAction,
                    aiAction.equals(consensus.getAction()) ? "AGREES" : "DISAGREES",
                    consensus.getSymbol(), ai.confidence());
        } catch (Exception e) {
            log.warn("AI-confirmer failed for {}: {}", consensus.getSymbol(), e.getMessage());
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ---------- helpers ----------

    private Set<Interval> intervals() {
        Set<Interval> cached = enabledIntervals;
        if (cached != null) return cached;
        Set<Interval> parsed = Arrays.stream(props.getDetector().getIntervals().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Interval.valueOf(s); }
                    catch (Exception e) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        enabledIntervals = parsed;
        return parsed;
    }

    private static Exchange angelExchangeCodeToEnum(int code) {
        return switch (code) {
            case 1 -> Exchange.NSE;
            case 2 -> Exchange.NFO;
            case 3 -> Exchange.BSE;
            case 4 -> Exchange.BFO;
            case 5 -> Exchange.MCX;
            case 13 -> Exchange.CDS;
            default -> Exchange.NSE;
        };
    }

    private static int estimateLookbackDays(Interval iv, int candlesWanted) {
        long minsPerBar = switch (iv) {
            case ONE_MINUTE -> 1; case FIVE_MINUTE -> 5;
            case FIFTEEN_MINUTE -> 15; case THIRTY_MINUTE -> 30;
            case ONE_HOUR -> 60; case ONE_DAY -> 390;
        };
        long minsWanted = minsPerBar * candlesWanted;
        int days = (int) Math.ceil((double) minsWanted / 390) * 2;   // 390 = trading mins/day
        return Math.max(days, 5);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    private static String strFromDetail(Map<String, Object> d, String key) {
        Object v = d.get(key);
        return v == null ? "" : v.toString();
    }

    private static java.math.BigDecimal toBd(Object v, java.math.BigDecimal fallback) {
        if (v == null) return fallback;
        if (v instanceof java.math.BigDecimal bd) return bd;
        if (v instanceof Number n) return java.math.BigDecimal.valueOf(n.doubleValue());
        try { return new java.math.BigDecimal(v.toString()); }
        catch (Exception e) { return fallback; }
    }
}
