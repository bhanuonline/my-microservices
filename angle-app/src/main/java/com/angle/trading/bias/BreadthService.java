package com.angle.trading.bias;

import com.angle.trading.bias.model.BreadthSection;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.marketdata.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Computes Advance/Decline breadth for the configured constituent universe.
 *
 * Strategy: for each constituent token, fetch the last few daily candles
 * → compare latest close vs previous close → count advance/decline/unchanged.
 *
 * Simplification: we use daily candles (1 call per stock) rather than a
 * dedicated batch-LTP endpoint. For a 50-stock universe, that's 50 API
 * calls per refresh — Angel handles it fine at 15-min cadence.
 *
 * If you later want higher-frequency breadth (every 1 min) you'd swap this
 * for Angel's batch quote API and cache aggressively.
 *
 * Failures on individual stocks are absorbed — one bad token doesn't kill
 * the entire breadth reading. If NO stocks succeed, breadth returns null.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BreadthService {

    private final MarketDataService marketDataService;
    private final BiasProperties biasProperties;

    public BreadthSection fetch() {
        BiasProperties.Breadth cfg = biasProperties.getBreadth();
        if (!cfg.isEnabled()) return null;
        if (cfg.getConstituentTokens().isEmpty()) {
            log.debug("Breadth enabled but constituent-tokens list is empty");
            return null;
        }

        int advances = 0, declines = 0, unchanged = 0, missing = 0;
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(7);

        for (String token : cfg.getConstituentTokens()) {
            try {
                List<Candle> candles = marketDataService.getCandles(
                        cfg.getBroker(),
                        Exchange.valueOf(cfg.getExchange()),
                        token,
                        Interval.ONE_DAY,
                        from, to);
                if (candles.size() < 2) { missing++; continue; }
                BigDecimal current = candles.get(candles.size() - 1).close();
                BigDecimal prev    = candles.get(candles.size() - 2).close();
                int cmp = current.compareTo(prev);
                if      (cmp > 0) advances++;
                else if (cmp < 0) declines++;
                else              unchanged++;
            } catch (Exception e) {
                missing++;
                log.debug("Breadth fetch failed for token {}: {}", token, e.getMessage());
            }
        }

        int totalCounted = advances + declines + unchanged;
        if (totalCounted == 0) {
            log.warn("Breadth: no constituents returned data (missing={})", missing);
            return null;
        }

        Double ratio = declines == 0
                ? (advances > 0 ? Double.POSITIVE_INFINITY : 0.0)
                : ((double) advances / declines);

        String regime = classify(ratio, cfg);
        String interpretation = interpret(advances, declines, ratio, regime);

        log.debug("Breadth: adv={} dec={} unch={} miss={} ratio={} regime={}",
                advances, declines, unchanged, missing, ratio, regime);

        return new BreadthSection(cfg.getLabel(), cfg.getConstituentTokens().size(),
                advances, declines, unchanged, ratio, regime, interpretation);
    }

    private static String classify(Double ratio, BiasProperties.Breadth cfg) {
        if (ratio == null) return null;
        if (Double.isInfinite(ratio))          return "STRONG_BULLISH";
        if (ratio >= cfg.getBullishRatio() * 1.5) return "STRONG_BULLISH";
        if (ratio >= cfg.getBullishRatio())    return "BULLISH";
        if (ratio <= cfg.getBearishRatio() / 1.5) return "STRONG_BEARISH";
        if (ratio <= cfg.getBearishRatio())    return "BEARISH";
        return "NEUTRAL";
    }

    private static String interpret(int adv, int dec, Double ratio, String regime) {
        if (regime == null) return "—";
        return switch (regime) {
            case "STRONG_BULLISH" -> String.format("Broad rally — %d up vs %d down", adv, dec);
            case "BULLISH"        -> String.format("Bullish breadth — %d up vs %d down", adv, dec);
            case "NEUTRAL"        -> String.format("Neutral / mixed — %d up vs %d down", adv, dec);
            case "BEARISH"        -> String.format("Bearish breadth — %d up vs %d down", adv, dec);
            case "STRONG_BEARISH" -> String.format("Broad selloff — %d up vs %d down", adv, dec);
            default -> "—";
        };
    }
}
