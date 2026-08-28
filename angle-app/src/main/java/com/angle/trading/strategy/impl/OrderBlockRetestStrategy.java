package com.angle.trading.strategy.impl;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.MarketContextBuilder;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.marketstructure.model.OrderBlock;
import com.angle.trading.strategy.Strategy;
import com.angle.trading.strategy.model.TradeIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SMC "Order Block Retest" strategy.
 *
 * Bullish setup:
 *   - Market bias is BULLISH (last structural event was a bullish BOS or CHoCH)
 *   - There's an unmitigated bullish OB at or below the current candle's low
 *   - The current candle's low taps INTO that OB (low ≤ OB.top AND low ≥ OB.bottom)
 *   ⇒ enter long at candle close
 *      stop   = OB.bottom
 *      target = nearest unswept BSL above current price
 *
 * Bearish setup is the mirror image (short at OB tap, stop at OB.top, target at unswept SSL).
 *
 * If there's no clean liquidity target above/below, the trade is skipped —
 * we won't enter without a defined take-profit.
 *
 * Complexity note: rebuilds MarketContext once per candle → O(n²) on the
 * candle count. Fine for backtests on a few hundred candles; the Phase 5
 * strategies will explore a streaming context to make this cheaper.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBlockRetestStrategy implements Strategy {

    private final MarketContextBuilder marketContextBuilder;

    @Override
    public String name() {
        return "ob-retest";
    }

    @Override
    public List<TradeIntent> evaluate(List<Candle> candles) {
        List<TradeIntent> intents = new ArrayList<>(candles.size());
        for (int i = 0; i < candles.size(); i++) {
            MarketContext ctx = marketContextBuilder.buildAsOf(candles, i);
            intents.add(intentAt(candles.get(i), ctx));
        }
        return intents;
    }

    private static TradeIntent intentAt(Candle c, MarketContext ctx) {
        if (ctx.isBullish()) {
            Optional<OrderBlock> obOpt = ctx.nearestBullishOBBelow(c.high());
            if (obOpt.isPresent() && candleTouchesOb(c, obOpt.get())) {
                OrderBlock ob = obOpt.get();
                Optional<BigDecimal> targetOpt = ctx.nearestUnsweptBSLAbove(c.close())
                        .map(l -> l.price());
                if (targetOpt.isPresent()) {
                    return TradeIntent.enterLong(
                            c.close(), ob.bottom(), targetOpt.get(),
                            "bullish OB retest @ " + ob.top() + "-" + ob.bottom()
                    );
                }
            }
        }

        if (ctx.isBearish()) {
            Optional<OrderBlock> obOpt = ctx.nearestBearishOBAbove(c.low());
            if (obOpt.isPresent() && candleTouchesOb(c, obOpt.get())) {
                OrderBlock ob = obOpt.get();
                Optional<BigDecimal> targetOpt = ctx.nearestUnsweptSSLBelow(c.close())
                        .map(l -> l.price());
                if (targetOpt.isPresent()) {
                    return TradeIntent.enterShort(
                            c.close(), ob.top(), targetOpt.get(),
                            "bearish OB retest @ " + ob.top() + "-" + ob.bottom()
                    );
                }
            }
        }
        return TradeIntent.hold();
    }

    /** Does this candle actually trade inside the OB zone? */
    private static boolean candleTouchesOb(Candle c, OrderBlock ob) {
        // Overlap = candle.low ≤ ob.top AND candle.high ≥ ob.bottom
        return c.low().compareTo(ob.top()) <= 0
                && c.high().compareTo(ob.bottom()) >= 0;
    }
}
