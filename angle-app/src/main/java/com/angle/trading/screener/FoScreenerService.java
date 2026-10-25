package com.angle.trading.screener;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.indicator.AverageDirectionalIndex;
import com.angle.trading.indicator.AverageTrueRange;
import com.angle.trading.indicator.ExponentialMovingAverage;
import com.angle.trading.indicator.RelativeStrengthIndex;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import com.angle.trading.persistence.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scans all tracked instruments on DAILY candles and scores each one
 * for F&O trade-readiness.
 *
 * Results are cached in-memory for 15 minutes so the dashboard page
 * loads instantly on repeat visits. Click "Re-scan" to force a refresh.
 *
 * Scoring (0–6):
 *   +1  ADX(14) > 25          strong trend
 *   +1  ADX(14) > 35          very strong trend bonus
 *   +1  Close > EMA(200)      bullish alignment
 *   +1  EMA(50) > EMA(200)    golden cross
 *   +1  RSI(14) in 40–65      healthy momentum, not overbought
 *   +1  Open signal exists    system already fired on this instrument
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FoScreenerService {

    private static final int  DAILY_LOOKBACK_DAYS = 260;   // ~1 trading year
    private static final long CACHE_TTL_SECONDS   = 15 * 60L;

    private final BiasInstrumentRepository instrumentRepo;
    private final MarketDataService         marketDataService;
    private final SignalRepository          signalRepo;

    // Simple in-memory cache
    private final AtomicReference<List<ScreenerRow>> cached    = new AtomicReference<>(List.of());
    private volatile Instant                          cachedAt  = Instant.EPOCH;

    // ── Public API ───────────────────────────────────────────────────

    public List<ScreenerRow> scan(boolean forceRefresh) {
        if (!forceRefresh && !isCacheStale()) {
            return cached.get();
        }
        List<ScreenerRow> rows = doScan();
        cached.set(rows);
        cachedAt = Instant.now();
        return rows;
    }

    public Instant lastScannedAt() { return cachedAt; }

    public boolean isCacheStale() {
        return Instant.now().isAfter(cachedAt.plusSeconds(CACHE_TTL_SECONDS));
    }

    // ── Core scan ────────────────────────────────────────────────────

    private List<ScreenerRow> doScan() {
        List<BiasInstrumentEntity> instruments = instrumentRepo.findAllByOrderByPriorityAscIdAsc()
                .stream().filter(BiasInstrumentEntity::isEnabled).toList();

        log.info("FoScreener: scanning {} instruments", instruments.size());

        List<ScreenerRow> rows = new ArrayList<>();
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(DAILY_LOOKBACK_DAYS);

        for (BiasInstrumentEntity instr : instruments) {
            rows.add(scoreInstrument(instr, from, to));
        }

        // Sort: error rows last, then by score desc, then by ADX desc
        rows.sort(Comparator
                .<ScreenerRow>comparingInt(r -> r.getError() != null ? 1 : 0)
                .thenComparingInt(r -> -r.getScore())
                .thenComparing(r -> r.getAdx() == null ? BigDecimal.ZERO : r.getAdx().negate()));

        log.info("FoScreener: done. Top score={}", rows.isEmpty() ? 0 : rows.get(0).getScore());
        return rows;
    }

    private ScreenerRow scoreInstrument(BiasInstrumentEntity instr, LocalDate from, LocalDate to) {
        Exchange exchange;
        try {
            exchange = Exchange.valueOf(instr.getExchange());
        } catch (IllegalArgumentException e) {
            exchange = Exchange.NSE;
        }

        List<Candle> candles;
        try {
            candles = marketDataService.getCandles(
                    instr.getBroker(), exchange, instr.getSymbolToken(),
                    Interval.ONE_DAY, from, to);
        } catch (Exception e) {
            log.warn("FoScreener: failed to fetch candles for {} — {}", instr.getSymbol(), e.getMessage());
            return ScreenerRow.builder()
                    .symbol(instr.getSymbol())
                    .symbolToken(instr.getSymbolToken())
                    .exchange(instr.getExchange())
                    .lotSize(instr.getLotSize())
                    .error("Data unavailable: " + e.getMessage())
                    .build();
        }

        if (candles == null || candles.size() < 50) {
            return ScreenerRow.builder()
                    .symbol(instr.getSymbol())
                    .symbolToken(instr.getSymbolToken())
                    .exchange(instr.getExchange())
                    .lotSize(instr.getLotSize())
                    .error("Insufficient data (" + (candles == null ? 0 : candles.size()) + " candles)")
                    .build();
        }

        // ── Compute indicators ──────────────────────────────────────

        List<BigDecimal> adxSeries  = new AverageDirectionalIndex(14).compute(candles);
        List<BigDecimal> rsiSeries  = new RelativeStrengthIndex(14).compute(candles);
        List<BigDecimal> ema50Series = new ExponentialMovingAverage(50).compute(candles);
        List<BigDecimal> ema200Series= new ExponentialMovingAverage(200).compute(candles);
        List<BigDecimal> atrSeries  = new AverageTrueRange(14).compute(candles);

        int last = candles.size() - 1;
        BigDecimal lastClose = candles.get(last).close();
        BigDecimal adx       = lastNonNull(adxSeries,   last);
        BigDecimal rsi       = lastNonNull(rsiSeries,   last);
        BigDecimal ema50     = lastNonNull(ema50Series,  last);
        BigDecimal ema200    = lastNonNull(ema200Series, last);
        BigDecimal atr       = lastNonNull(atrSeries,   last);

        // ── Score criteria ──────────────────────────────────────────

        boolean adxStrong   = adx   != null && adx.compareTo(BigDecimal.valueOf(25)) >= 0;
        boolean adxVStrong  = adx   != null && adx.compareTo(BigDecimal.valueOf(35)) >= 0;
        boolean aboveEma200 = ema200 != null && lastClose.compareTo(ema200) > 0;
        boolean emaCross    = ema50 != null && ema200 != null && ema50.compareTo(ema200) > 0;
        boolean rsiHealthy  = rsi   != null
                && rsi.compareTo(BigDecimal.valueOf(40)) >= 0
                && rsi.compareTo(BigDecimal.valueOf(65)) <= 0;

        int openSigCount = signalRepo.findByStatusAndSymbolToken("OPEN", instr.getSymbolToken()).size();
        boolean hasSignal = openSigCount > 0;

        int score = 0;
        if (adxStrong)   score++;
        if (adxVStrong)  score++;
        if (aboveEma200) score++;
        if (emaCross)    score++;
        if (rsiHealthy)  score++;
        if (hasSignal)   score++;

        // ── Trend bias ──────────────────────────────────────────────
        String trendBias;
        if (ema200 == null) {
            trendBias = "NEUTRAL";
        } else if (lastClose.compareTo(ema200) > 0 && (ema50 == null || ema50.compareTo(ema200) > 0)) {
            trendBias = "BULLISH";
        } else if (lastClose.compareTo(ema200) < 0 && (ema50 == null || ema50.compareTo(ema200) < 0)) {
            trendBias = "BEARISH";
        } else {
            trendBias = "MIXED";
        }

        return ScreenerRow.builder()
                .symbol(instr.getSymbol())
                .symbolToken(instr.getSymbolToken())
                .exchange(instr.getExchange())
                .lotSize(instr.getLotSize())
                .lastClose(round2(lastClose))
                .ema50(round2(ema50))
                .ema200(round2(ema200))
                .rsi(round2(rsi))
                .adx(round2(adx))
                .atr(round2(atr))
                .trendBias(trendBias)
                .score(score)
                .openSignals(openSigCount)
                .adxStrong(adxStrong)
                .aboveEma200(aboveEma200)
                .emaCross(emaCross)
                .rsiHealthy(rsiHealthy)
                .hasSignal(hasSignal)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private static BigDecimal lastNonNull(List<BigDecimal> series, int from) {
        for (int i = from; i >= 0; i--) {
            if (series.get(i) != null) return series.get(i);
        }
        return null;
    }

    private static BigDecimal round2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
