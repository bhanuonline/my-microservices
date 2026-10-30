package com.angle.trading.analysis;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.dto.FvgZoneDto;
import com.angle.trading.dto.MarketStructureDto;
import com.angle.trading.dto.OrderBlockZoneDto;
import com.angle.trading.dto.SmartMoneyDto;
import com.angle.trading.marketstructure.FvgDetector;
import com.angle.trading.marketstructure.LiquidityDetector;
import com.angle.trading.marketstructure.OrderBlockDetector;
import com.angle.trading.marketstructure.StructureAnalyzer;
import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.FairValueGap;
import com.angle.trading.marketstructure.model.LiquiditySide;
import com.angle.trading.marketstructure.model.OrderBlock;
import com.angle.trading.marketstructure.model.StructureEvent;
import com.angle.trading.marketstructure.model.SwingPoint;
import com.angle.trading.marketstructure.model.SwingType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Runs all four SMC detectors on a candle series and returns
 * MarketStructureDto + SmartMoneyDto for the current (last) candle.
 */
@Service
@RequiredArgsConstructor
public class SmcAnalysisService {

    private final StructureAnalyzer  structureAnalyzer;
    private final OrderBlockDetector orderBlockDetector;
    private final FvgDetector        fvgDetector;
    private final LiquidityDetector  liquidityDetector;

    public record Result(MarketStructureDto marketStructure, SmartMoneyDto smartMoney) {}

    public Result analyze(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return new Result(null, null);
        BigDecimal close = candles.get(candles.size() - 1).close();
        return analyze(candles, close);
    }

    public Result analyze(List<Candle> candles, BigDecimal close) {
        if (candles == null || candles.isEmpty()) return new Result(null, null);
        try {
            StructureAnalyzer.Result sr = structureAnalyzer.analyze(candles);
            List<OrderBlock>   obs  = orderBlockDetector.detect(candles, sr.events());
            List<FairValueGap> fvgs = fvgDetector.detect(candles);
            LiquidityDetector.Result liq = liquidityDetector.detect(candles, sr.swings());

            return new Result(
                    buildMarketStructure(sr, close),
                    buildSmartMoney(obs, fvgs, liq, close)
            );
        } catch (Exception e) {
            return new Result(null, null);
        }
    }

    // -------------------------------------------------------------------------

    private static MarketStructureDto buildMarketStructure(StructureAnalyzer.Result sr,
                                                            BigDecimal close) {
        List<StructureEvent> events = sr.events();
        StructureEvent last = events.isEmpty() ? null : events.get(events.size() - 1);

        String bias      = last == null ? null : last.direction().name();
        String lastEvent = last == null ? null
                : last.type().name() + "_" + last.direction().name();

        List<SwingPoint> swings = sr.swings();

        // Nearest swing high ABOVE current price → resistance
        BigDecimal nearestHigh = swings.stream()
                .filter(s -> s.type() == SwingType.HIGH)
                .filter(s -> close == null || s.price().compareTo(close) > 0)
                .map(SwingPoint::price)
                .min(BigDecimal::compareTo)
                .orElse(null);

        // Nearest swing low BELOW current price → support
        BigDecimal nearestLow = swings.stream()
                .filter(s -> s.type() == SwingType.LOW)
                .filter(s -> close == null || s.price().compareTo(close) < 0)
                .map(SwingPoint::price)
                .max(BigDecimal::compareTo)
                .orElse(null);

        return new MarketStructureDto(bias, lastEvent, nearestHigh, nearestLow);
    }

    private static SmartMoneyDto buildSmartMoney(List<OrderBlock> obs,
                                                   List<FairValueGap> fvgs,
                                                   LiquidityDetector.Result liq,
                                                   BigDecimal close) {
        // Nearest unmitigated BULLISH OB below price (support zone)
        OrderBlockZoneDto bullishOB = obs.stream()
                .filter(ob -> !ob.mitigated() && ob.direction() == Direction.BULLISH)
                .filter(ob -> close == null || ob.top().compareTo(close) <= 0)
                .max(Comparator.comparing(OrderBlock::top))
                .map(ob -> new OrderBlockZoneDto(ob.top(), ob.bottom(), "BULLISH"))
                .orElse(null);

        // Nearest unmitigated BEARISH OB above price (resistance zone)
        OrderBlockZoneDto bearishOB = obs.stream()
                .filter(ob -> !ob.mitigated() && ob.direction() == Direction.BEARISH)
                .filter(ob -> close == null || ob.bottom().compareTo(close) >= 0)
                .min(Comparator.comparing(OrderBlock::bottom))
                .map(ob -> new OrderBlockZoneDto(ob.top(), ob.bottom(), "BEARISH"))
                .orElse(null);

        // Unmitigated FVGs — up to 5, closest to price first
        List<FvgZoneDto> openFvgs = fvgs.stream()
                .filter(fvg -> !fvg.mitigated())
                .sorted(Comparator.comparing(fvg -> distanceToPrice(fvg, close)))
                .limit(5)
                .map(fvg -> new FvgZoneDto(fvg.top(), fvg.bottom(), fvg.direction().name()))
                .toList();

        // Nearest unswept BUY-SIDE liquidity above price (short-stop target)
        BigDecimal buySideAbove = liq.levels().stream()
                .filter(l -> !l.swept() && l.side() == LiquiditySide.BUY_SIDE)
                .filter(l -> close == null || l.price().compareTo(close) > 0)
                .map(com.angle.trading.marketstructure.model.LiquidityLevel::price)
                .min(BigDecimal::compareTo)
                .orElse(null);

        // Nearest unswept SELL-SIDE liquidity below price (long-stop target)
        BigDecimal sellSideBelow = liq.levels().stream()
                .filter(l -> !l.swept() && l.side() == LiquiditySide.SELL_SIDE)
                .filter(l -> close == null || l.price().compareTo(close) < 0)
                .map(com.angle.trading.marketstructure.model.LiquidityLevel::price)
                .max(BigDecimal::compareTo)
                .orElse(null);

        return new SmartMoneyDto(bullishOB, bearishOB, openFvgs, buySideAbove, sellSideBelow);
    }

    private static BigDecimal distanceToPrice(FairValueGap fvg, BigDecimal close) {
        if (close == null) return fvg.top();
        BigDecimal mid = fvg.top().add(fvg.bottom()).divide(BigDecimal.valueOf(2));
        return mid.subtract(close).abs();
    }
}
