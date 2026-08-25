package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.StructureEvent;
import com.angle.trading.marketstructure.model.StructureEventType;
import com.angle.trading.marketstructure.model.SwingPoint;
import com.angle.trading.marketstructure.model.SwingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks candles chronologically, tracks the most recent swing high and swing
 * low, and emits BOS / CHoCH events whenever a candle CLOSE breaks one.
 *
 * State machine:
 *   bias = NONE at start
 *   on close > latestSwingHigh.price:
 *     bias == BULLISH → BOS bullish        (continuation)
 *     bias != BULLISH → CHoCH bullish      (reversal), flip bias to BULLISH
 *   on close < latestSwingLow.price:
 *     bias == BEARISH → BOS bearish
 *     bias != BEARISH → CHoCH bearish, flip bias to BEARISH
 *
 * After each break, the broken swing is retired — we wait for a new swing
 * on that side (via SwingDetector output) before another break can fire.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureAnalyzer {

    private final SwingDetector swingDetector;

    public Result analyze(List<Candle> candles) {
        List<SwingPoint> swings = swingDetector.detect(candles);
        List<StructureEvent> events = new ArrayList<>();
        if (swings.isEmpty()) return new Result(swings, events);

        // Index swings by their candle index so we can pick them up chronologically.
        int swingIx = 0;
        SwingPoint lastHigh = null;
        SwingPoint lastLow  = null;
        Direction  bias     = null;

        for (int i = 0; i < candles.size(); i++) {
            // Absorb any swings that confirmed at or before this candle.
            while (swingIx < swings.size() && swings.get(swingIx).index() <= i) {
                SwingPoint sp = swings.get(swingIx++);
                if (sp.type() == SwingType.HIGH) lastHigh = sp;
                else                             lastLow  = sp;
            }

            Candle c = candles.get(i);

            if (lastHigh != null && i > lastHigh.index() && c.close().compareTo(lastHigh.price()) > 0) {
                StructureEventType type = (bias == Direction.BULLISH)
                        ? StructureEventType.BOS
                        : StructureEventType.CHOCH;
                events.add(new StructureEvent(
                        i, c.timestamp(), type, Direction.BULLISH,
                        lastHigh.price(), lastHigh.index()));
                bias = Direction.BULLISH;
                lastHigh = null;       // retire — wait for next swing high
                continue;
            }

            if (lastLow != null && i > lastLow.index() && c.close().compareTo(lastLow.price()) < 0) {
                StructureEventType type = (bias == Direction.BEARISH)
                        ? StructureEventType.BOS
                        : StructureEventType.CHOCH;
                events.add(new StructureEvent(
                        i, c.timestamp(), type, Direction.BEARISH,
                        lastLow.price(), lastLow.index()));
                bias = Direction.BEARISH;
                lastLow = null;
            }
        }

        log.debug("StructureAnalyzer: {} candles, {} swings, {} events",
                candles.size(), swings.size(), events.size());
        return new Result(swings, events);
    }

    /** Combined analysis output: the raw swings and the derived events. */
    public record Result(List<SwingPoint> swings, List<StructureEvent> events) {}
}
