package com.angle.trading.marketstructure;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.FairValueGap;
import com.angle.trading.marketstructure.model.LiquidityLevel;
import com.angle.trading.marketstructure.model.LiquiditySide;
import com.angle.trading.marketstructure.model.MarketContext;
import com.angle.trading.marketstructure.model.OrderBlock;
import com.angle.trading.marketstructure.model.StructureEvent;
import com.angle.trading.marketstructure.model.SwingPoint;
import com.angle.trading.marketstructure.model.SwingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Assembles a {@link MarketContext} at a given candle by running every
 * Phase 1 + Phase 2 detector on the sub-list of candles up to that index.
 *
 * Because each detector only looks at the candles handed to it, calling
 * {@code buildAsOf(candles, i)} guarantees no future data leaks into the
 * snapshot — safe for backtesting.
 *
 * Convenience {@code build(candles)} returns the context at the end of the
 * whole series (i.e. "right now").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketContextBuilder {

    private final StructureAnalyzer structureAnalyzer;
    private final OrderBlockDetector orderBlockDetector;
    private final FvgDetector fvgDetector;
    private final LiquidityDetector liquidityDetector;

    /** Context at the last candle in the series. */
    public MarketContext build(List<Candle> candles) {
        return buildAsOf(candles, candles.size() - 1);
    }

    /** Context at a specific candle index — everything after {@code asOfIx} is invisible. */
    public MarketContext buildAsOf(List<Candle> candles, int asOfIx) {
        if (candles.isEmpty() || asOfIx < 0) {
            return empty();
        }
        if (asOfIx >= candles.size()) asOfIx = candles.size() - 1;

        List<Candle> visible = candles.subList(0, asOfIx + 1);
        Candle current = candles.get(asOfIx);

        StructureAnalyzer.Result structure = structureAnalyzer.analyze(visible);
        List<OrderBlock>   orderBlocks = orderBlockDetector.detect(visible, structure.events());
        List<FairValueGap> fvgs        = fvgDetector.detect(visible);
        LiquidityDetector.Result liquidity = liquidityDetector.detect(visible, structure.swings());

        Direction bias = structure.events().isEmpty()
                ? null
                : structure.events().get(structure.events().size() - 1).direction();

        StructureEvent lastEvent = structure.events().isEmpty()
                ? null
                : structure.events().get(structure.events().size() - 1);

        SwingPoint latestSwingHigh = lastSwingOfType(structure.swings(), SwingType.HIGH);
        SwingPoint latestSwingLow  = lastSwingOfType(structure.swings(), SwingType.LOW);

        List<OrderBlock> bullOBs = orderBlocks.stream()
                .filter(ob -> !ob.mitigated() && ob.direction() == Direction.BULLISH)
                .toList();
        List<OrderBlock> bearOBs = orderBlocks.stream()
                .filter(ob -> !ob.mitigated() && ob.direction() == Direction.BEARISH)
                .toList();
        List<FairValueGap> bullFvgs = fvgs.stream()
                .filter(f -> !f.mitigated() && f.direction() == Direction.BULLISH)
                .toList();
        List<FairValueGap> bearFvgs = fvgs.stream()
                .filter(f -> !f.mitigated() && f.direction() == Direction.BEARISH)
                .toList();
        List<LiquidityLevel> unsweptBSL = liquidity.levels().stream()
                .filter(l -> !l.swept() && l.side() == LiquiditySide.BUY_SIDE)
                .toList();
        List<LiquidityLevel> unsweptSSL = liquidity.levels().stream()
                .filter(l -> !l.swept() && l.side() == LiquiditySide.SELL_SIDE)
                .toList();

        MarketContext ctx = new MarketContext(
                asOfIx, current.timestamp(), current.close(),
                bias, lastEvent,
                latestSwingHigh, latestSwingLow,
                bullOBs, bearOBs,
                bullFvgs, bearFvgs,
                unsweptBSL, unsweptSSL,
                liquidity.sweeps()
        );

        log.debug("MarketContext@{}: bias={}, activeBullOBs={}, activeBearOBs={}, unsweptBSL={}, unsweptSSL={}",
                asOfIx, bias, bullOBs.size(), bearOBs.size(),
                unsweptBSL.size(), unsweptSSL.size());
        return ctx;
    }

    private static SwingPoint lastSwingOfType(List<SwingPoint> swings, SwingType type) {
        for (int i = swings.size() - 1; i >= 0; i--) {
            if (swings.get(i).type() == type) return swings.get(i);
        }
        return null;
    }

    private static MarketContext empty() {
        return new MarketContext(
                -1, Instant.EPOCH, null, null, null, null, null,
                List.of(), List.of(),
                List.of(), List.of(),
                List.of(), List.of(),
                List.of()
        );
    }
}
