package com.angle.trading.bias;

import com.angle.trading.bias.model.VixSection;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Fetches India VIX (or any volatility index) from the configured broker.
 *
 * Uses daily candles — VIX doesn't need intraday granularity for the
 * regime labelling we do.
 *
 * Two entry points:
 *   fetchLatest()        — as of right now
 *   fetchAsOf(instant)   — as of a past date (for historical dashboard view)
 *
 * Returns null-populated section if the broker call fails; UI shows "—".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VixService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final MarketDataService marketDataService;
    private final BiasProperties biasProperties;

    public VixSection fetchLatest() {
        return fetchAsOf(null);
    }

    /** Fetch VIX as of a past instant; null asOf = latest. */
    public VixSection fetchAsOf(Instant asOf) {
        BiasProperties.Vix cfg = biasProperties.getVix();
        if (!cfg.isEnabled()) {
            return new VixSection(null, null, null, null);
        }
        try {
            LocalDate to = asOf == null ? LocalDate.now()
                    : asOf.atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate from = to.minusDays(10);
            List<Candle> candles = marketDataService.getCandles(
                    "ANGEL",
                    Exchange.valueOf(cfg.getExchange()),
                    cfg.getSymbolToken(),
                    Interval.ONE_DAY,
                    from, to);
            if (asOf != null) {
                candles = candles.stream().filter(c -> !c.timestamp().isAfter(asOf)).toList();
            }
            if (candles.isEmpty()) {
                log.debug("VIX fetch returned no candles (asOf={})", asOf);
                return new VixSection(null, null, null, null);
            }
            BigDecimal current = candles.get(candles.size() - 1).close();
            BigDecimal prev    = candles.size() >= 2 ? candles.get(candles.size() - 2).close() : null;
            BigDecimal change = null;
            BigDecimal changePct = null;
            if (prev != null && prev.signum() > 0) {
                change = current.subtract(prev);
                changePct = change.divide(prev, MC).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            }
            String regime = classifyRegime(current.doubleValue(), cfg);
            return new VixSection(current, change, changePct, regime);
        } catch (Exception e) {
            log.warn("VIX fetch failed: {}", e.getMessage());
            return new VixSection(null, null, null, null);
        }
    }

    private static String classifyRegime(double value, BiasProperties.Vix cfg) {
        if (value < cfg.getCalmThreshold())  return "CALM";
        if (value < cfg.getSpikeThreshold()) return "NORMAL";
        return "ELEVATED";
    }
}
