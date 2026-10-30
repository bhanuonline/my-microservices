package com.angle.trading.cache;

import com.angle.trading.dto.IndicatorSnapshotDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.angle.trading.dto.OrbDto;
import com.angle.trading.dto.OrbTimeframeDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One indicator snapshot — values for a single candle bar of a given symbol + interval.
 *
 * Unique on (symbol, interval_name, candle_time) so re-saving the same bar is idempotent
 * (old row is deleted before the new batch is inserted).
 *
 * Used as L3 in the three-layer cache:  JVM → Redis → DB
 */
@Entity
@Table(
    name = "indicator_snapshot",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ind_symbol_interval_time",
        columnNames = {"symbol", "interval_name", "candle_time"}
    ),
    indexes = {
        @Index(name = "idx_ind_symbol_interval", columnList = "symbol, interval_name"),
        @Index(name = "idx_ind_candle_time",     columnList = "candle_time")
    }
)
@Data
@NoArgsConstructor
public class IndicatorSnapshotEntity {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(IST);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(name = "interval_name", nullable = false, length = 32)
    private String interval;

    @Column(name = "candle_time", nullable = false)
    private Instant candleTime;

    @Column(precision = 20, scale = 4) private BigDecimal rsi14;
    @Column(precision = 20, scale = 4) private BigDecimal adx14;
    @Column(name = "plus_di",  precision = 20, scale = 4) private BigDecimal plusDi;
    @Column(name = "minus_di", precision = 20, scale = 4) private BigDecimal minusDi;
    @Column(precision = 20, scale = 4) private BigDecimal atr14;
    @Column(precision = 20, scale = 4) private BigDecimal ema9;
    @Column(precision = 20, scale = 4) private BigDecimal ema20;
    @Column(precision = 20, scale = 4) private BigDecimal ema50;
    @Column(precision = 20, scale = 4) private BigDecimal ema200;
    @Column(precision = 20, scale = 4) private BigDecimal sma20;
    @Column(name = "macd_line",      precision = 20, scale = 4) private BigDecimal macdLine;
    @Column(name = "macd_signal",    precision = 20, scale = 4) private BigDecimal macdSignal;
    @Column(name = "macd_histogram", precision = 20, scale = 4) private BigDecimal macdHistogram;
    @Column(precision = 20, scale = 4)                          private BigDecimal vwap;
    @Column(name = "supertrend_line", precision = 20, scale = 4) private BigDecimal supertrendLine;
    @Column(name = "supertrend_bullish")                         private Boolean    supertrendBullish;
    @Column(name = "bb_upper",  precision = 20, scale = 4)      private BigDecimal bbUpper;
    @Column(name = "bb_middle", precision = 20, scale = 4)      private BigDecimal bbMiddle;
    @Column(name = "bb_lower",  precision = 20, scale = 4)      private BigDecimal bbLower;

    @Column(name = "close_price", precision = 20, scale = 4)
    private BigDecimal closePrice;

    // ── Opening Range Breakout (point-in-time snapshot) ─────────
    @Column(name = "orb_15m_signal", length = 10) private String     orb15mSignal;
    @Column(name = "orb_15m_high",   precision = 20, scale = 4) private BigDecimal orb15mHigh;
    @Column(name = "orb_15m_low",    precision = 20, scale = 4) private BigDecimal orb15mLow;

    @Column(name = "orb_30m_signal", length = 10) private String     orb30mSignal;
    @Column(name = "orb_30m_high",   precision = 20, scale = 4) private BigDecimal orb30mHigh;
    @Column(name = "orb_30m_low",    precision = 20, scale = 4) private BigDecimal orb30mLow;

    @Column(name = "orb_60m_signal", length = 10) private String     orb60mSignal;
    @Column(name = "orb_60m_high",   precision = 20, scale = 4) private BigDecimal orb60mHigh;
    @Column(name = "orb_60m_low",    precision = 20, scale = 4) private BigDecimal orb60mLow;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public static IndicatorSnapshotEntity from(String symbol, String interval, IndicatorSnapshotDto dto) {
        IndicatorSnapshotEntity e = new IndicatorSnapshotEntity();
        e.symbol     = symbol;
        e.interval   = interval;
        e.candleTime = dto.time();
        e.computedAt = Instant.now();

        if (dto.momentum() != null) {
            e.rsi14 = dto.momentum().rsi14();
            if (dto.momentum().macd() != null) {
                e.macdLine      = dto.momentum().macd().line();
                e.macdSignal    = dto.momentum().macd().signal();
                e.macdHistogram = dto.momentum().macd().histogram();
            }
        }
        if (dto.trend() != null) {
            e.adx14   = dto.trend().adx14();
            e.plusDi  = dto.trend().plusDi();
            e.minusDi = dto.trend().minusDi();
            if (dto.trend().supertrend() != null) {
                e.supertrendLine    = dto.trend().supertrend().line();
                e.supertrendBullish = dto.trend().supertrend().bullish();
            }
        }
        if (dto.movingAverages() != null) {
            e.ema9  = dto.movingAverages().ema9();
            e.ema20 = dto.movingAverages().ema20();
            e.ema50 = dto.movingAverages().ema50();
            e.ema200= dto.movingAverages().ema200();
            e.sma20 = dto.movingAverages().sma20();
            e.vwap  = dto.movingAverages().vwap();
        }
        if (dto.volatility() != null) {
            e.atr14 = dto.volatility().atr14();
            if (dto.volatility().bollingerBands() != null) {
                e.bbUpper  = dto.volatility().bollingerBands().upper();
                e.bbMiddle = dto.volatility().bollingerBands().middle();
                e.bbLower  = dto.volatility().bollingerBands().lower();
            }
        }
        if (dto.candle() != null) {
            e.closePrice = dto.candle().close();
        }
        if (dto.orb() != null && dto.orb().timeframes() != null) {
            dto.orb().timeframes().forEach(tf -> {
                switch (tf.minutes()) {
                    case 15 -> { e.orb15mSignal = tf.signal(); e.orb15mHigh = tf.orHigh(); e.orb15mLow = tf.orLow(); }
                    case 30 -> { e.orb30mSignal = tf.signal(); e.orb30mHigh = tf.orHigh(); e.orb30mLow = tf.orLow(); }
                    case 60 -> { e.orb60mSignal = tf.signal(); e.orb60mHigh = tf.orHigh(); e.orb60mLow = tf.orLow(); }
                }
            });
        }
        return e;
    }

    private OrbDto buildOrbFromDb() {
        List<OrbTimeframeDto> tfs = new ArrayList<>();
        if (orb15mSignal != null)
            tfs.add(new OrbTimeframeDto(15, orb15mSignal, orb15mHigh, orb15mLow,
                    orb15mHigh != null && orb15mLow != null ? orb15mHigh.subtract(orb15mLow) : null));
        if (orb30mSignal != null)
            tfs.add(new OrbTimeframeDto(30, orb30mSignal, orb30mHigh, orb30mLow,
                    orb30mHigh != null && orb30mLow != null ? orb30mHigh.subtract(orb30mLow) : null));
        if (orb60mSignal != null)
            tfs.add(new OrbTimeframeDto(60, orb60mSignal, orb60mHigh, orb60mLow,
                    orb60mHigh != null && orb60mLow != null ? orb60mHigh.subtract(orb60mLow) : null));
        return tfs.isEmpty() ? null : new OrbDto(tfs);
    }

    public IndicatorSnapshotDto toDto() {
        com.angle.trading.dto.MovingAveragesDto ma =
                new com.angle.trading.dto.MovingAveragesDto(ema9, ema20, ema50, ema200, sma20, vwap);
        com.angle.trading.dto.SupertrendDto st =
                new com.angle.trading.dto.SupertrendDto(supertrendLine, supertrendBullish);
        com.angle.trading.dto.BollingerBandsDto bb =
                new com.angle.trading.dto.BollingerBandsDto(bbUpper, bbMiddle, bbLower);
        com.angle.trading.dto.VolatilityDto vol =
                new com.angle.trading.dto.VolatilityDto(atr14, bb);
        com.angle.trading.dto.TrendDto trend =
                new com.angle.trading.dto.TrendDto(adx14, plusDi, minusDi, st);
        com.angle.trading.dto.MomentumDto momentum =
                new com.angle.trading.dto.MomentumDto(rsi14,
                        new com.angle.trading.dto.MacdDto(macdLine, macdSignal, macdHistogram));

        return new IndicatorSnapshotDto(
                candleTime, IST_FMT.format(candleTime),
                null,    // candle OHLCV not stored in DB
                momentum, trend, ma, vol,
                com.angle.trading.indicator.IndicatorSignals.buildSignals(closePrice, rsi14, adx14, macdHistogram, st, bb, vwap),
                com.angle.trading.indicator.IndicatorSignals.buildCrossovers(closePrice, ma),
                null,    // marketStructure — recomputed on fresh fetch, not stored in DB
                null,    // smartMoney     — recomputed on fresh fetch, not stored in DB
                null,    // pivotPoints    — recomputed on fresh fetch, not stored in DB
                buildOrbFromDb()
        );
    }
}
