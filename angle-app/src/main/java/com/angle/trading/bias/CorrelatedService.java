package com.angle.trading.bias;

import com.angle.trading.bias.model.CorrelatedSection;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.marketdata.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Fetches the correlated instrument (e.g. Bank Nifty) and compares its
 * intraday % move with the main instrument's move.
 *
 * If both are moving in the same direction with similar magnitude → real trend.
 * If they diverge (main up but correlated flat / down) → warning: fake move.
 *
 * The main instrument's day % move is passed in — we don't recompute it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelatedService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MarketDataService marketDataService;
    private final BiasProperties biasProperties;

    public CorrelatedSection fetch(BigDecimal mainDayChangePercent) {
        BiasProperties.Correlated cfg = biasProperties.getCorrelated();
        if (!cfg.isEnabled()) {
            return null;
        }
        try {
            LocalDate to   = LocalDate.now();
            LocalDate from = to.minusDays(5);   // enough for prev close + today
            List<Candle> candles = marketDataService.getCandles(
                    cfg.getBroker(),
                    Exchange.valueOf(cfg.getExchange()),
                    cfg.getSymbolToken(),
                    Interval.ONE_DAY,
                    from, to);
            if (candles.isEmpty()) {
                log.debug("Correlated fetch returned no candles for {}", cfg.getSymbol());
                return emptySection(cfg);
            }

            BigDecimal current = candles.get(candles.size() - 1).close();
            BigDecimal prev = candles.size() >= 2 ? candles.get(candles.size() - 2).close() : null;
            BigDecimal correlatedPct = null;
            if (prev != null && prev.signum() > 0) {
                correlatedPct = current.subtract(prev)
                        .divide(prev, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal divergence = null;
            boolean diverging = false;
            boolean directionAgreement = true;
            String signal = "—";
            if (correlatedPct != null && mainDayChangePercent != null) {
                divergence = correlatedPct.subtract(mainDayChangePercent).abs()
                        .setScale(2, RoundingMode.HALF_UP);
                diverging = divergence.doubleValue() > cfg.getDivergencePercentThreshold();
                directionAgreement = sameSign(correlatedPct, mainDayChangePercent);
                signal = interpret(mainDayChangePercent, correlatedPct, diverging, directionAgreement);
            }
            return new CorrelatedSection(cfg.getSymbol(), current, correlatedPct,
                    mainDayChangePercent, divergence, diverging, directionAgreement, signal);
        } catch (Exception e) {
            log.warn("Correlated fetch failed: {}", e.getMessage());
            return emptySection(cfg);
        }
    }

    private static CorrelatedSection emptySection(BiasProperties.Correlated cfg) {
        return new CorrelatedSection(cfg.getSymbol(), null, null, null, null, false, false, "unavailable");
    }

    private static boolean sameSign(BigDecimal a, BigDecimal b) {
        return a.signum() == b.signum() || a.signum() == 0 || b.signum() == 0;
    }

    private static String interpret(BigDecimal main, BigDecimal correlated, boolean diverging, boolean sameDir) {
        boolean mainUp = main.signum() > 0;
        boolean corrUp = correlated.signum() > 0;
        if (!sameDir) {
            return mainUp
                    ? "DIVERGENT — main up, correlated down (fake rally risk)"
                    : "DIVERGENT — main down, correlated up (fake selloff risk)";
        }
        if (diverging) {
            return mainUp
                    ? "PARTIAL confirmation — both up but magnitude differs"
                    : "PARTIAL confirmation — both down but magnitude differs";
        }
        if (main.signum() == 0 && correlated.signum() == 0) return "Flat — no strong signal";
        return mainUp ? "CONFIRMED bullish — both up in sync" : "CONFIRMED bearish — both down in sync";
    }
}
