package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.LiquiditySide;
import com.angle.trading.marketstructure.model.LiquiditySweep;
import com.angle.trading.marketstructure.model.SwingPoint;
import com.angle.trading.marketstructure.model.SwingType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns swings into liquidity levels and detects sweeps.
 *
 * Every swing high is a BUY_SIDE level (stops of shorts). Every swing low
 * is a SELL_SIDE level.
 *
 * A sweep is a later candle whose:
 *   - high  >  level (BUY_SIDE)  AND close < level  → shorts stopped, price rejected
 *   - low   <  level (SELL_SIDE) AND close > level  → longs stopped, price rejected
 *
 * The FIRST sweep marks the level as swept; further piercings are ignored
 * (Phase 3 will treat re-taps differently).
 */
@Service
public class LiquidityDetector {

    public Result detect(List<Candle> candles, List<SwingPoint> swings) {
        List<LiquidityLevel> levels = new ArrayList<>(swings.size());
        List<LiquiditySweep> sweeps = new ArrayList<>();

        for (SwingPoint sp : swings) {
            LiquiditySide side = (sp.type() == SwingType.HIGH)
                    ? LiquiditySide.BUY_SIDE
                    : LiquiditySide.SELL_SIDE;

            Integer sweepIx = findSweep(candles, sp.index(), sp.price(), side);
            if (sweepIx != null) {
                sweeps.add(new LiquiditySweep(
                        sweepIx,
                        candles.get(sweepIx).timestamp(),
                        sp.price(),
                        side,
                        sp.index()
                ));
            }
            levels.add(new LiquidityLevel(
                    sp.timestamp(),
                    sp.price(),
                    side,
                    sp.index(),
                    sweepIx != null,
                    sweepIx
            ));
        }
        return new Result(levels, sweeps);
    }

    private static Integer findSweep(List<Candle> candles, int startIx,
                                     BigDecimal level, LiquiditySide side) {
        for (int j = startIx + 1; j < candles.size(); j++) {
            Candle c = candles.get(j);
            if (side == LiquiditySide.BUY_SIDE) {
                if (c.high().compareTo(level) > 0 && c.close().compareTo(level) < 0) return j;
            } else {
                if (c.low().compareTo(level)  < 0 && c.close().compareTo(level) > 0) return j;
            }
        }
        return null;
    }

    public record Result(List<LiquidityLevel> levels, List<LiquiditySweep> sweeps) {}
}
