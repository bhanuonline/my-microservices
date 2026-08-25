package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.FairValueGap;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects 3-candle imbalances.
 *
 *   Bullish FVG at i:  candle[i-2].high < candle[i].low
 *     gap = [ candle[i-2].high , candle[i].low ]
 *
 *   Bearish FVG at i:  candle[i-2].low  > candle[i].high
 *     gap = [ candle[i].high , candle[i-2].low ]
 *
 * The middle candle (i-1) is intentionally ignored — it's the impulse that
 * created the gap; the imbalance is defined by the outer two candles.
 *
 * Mitigation: any later candle whose low ≤ top AND high ≥ bottom taps the
 * gap. Records the FIRST tap; further taps ignored for now.
 */
@Service
public class FvgDetector {

    public List<FairValueGap> detect(List<Candle> candles) {
        List<FairValueGap> out = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            Candle c1 = candles.get(i - 2);
            Candle c3 = candles.get(i);

            // Bullish gap
            if (c1.high().compareTo(c3.low()) < 0) {
                out.add(buildFvg(candles, i, c3.timestamp(),
                        c3.low(), c1.high(), Direction.BULLISH));
                continue;
            }
            // Bearish gap
            if (c1.low().compareTo(c3.high()) > 0) {
                out.add(buildFvg(candles, i, c3.timestamp(),
                        c1.low(), c3.high(), Direction.BEARISH));
            }
        }
        return out;
    }

    private static FairValueGap buildFvg(List<Candle> candles, int formedAt,
                                          java.time.Instant ts,
                                          BigDecimal top, BigDecimal bottom,
                                          Direction dir) {
        Integer mitigatedAt = null;
        for (int j = formedAt + 1; j < candles.size(); j++) {
            Candle later = candles.get(j);
            if (later.low().compareTo(top) <= 0
                    && later.high().compareTo(bottom) >= 0) {
                mitigatedAt = j;
                break;
            }
        }
        return new FairValueGap(ts, top, bottom, dir, formedAt,
                mitigatedAt != null, mitigatedAt);
    }
}
