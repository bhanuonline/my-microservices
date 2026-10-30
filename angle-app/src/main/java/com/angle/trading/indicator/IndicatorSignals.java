package com.angle.trading.indicator;

import com.angle.trading.dto.BollingerBandsDto;
import com.angle.trading.dto.CrossoversDto;
import com.angle.trading.dto.MovingAveragesDto;
import com.angle.trading.dto.SignalsDto;
import com.angle.trading.dto.SupertrendDto;

import java.math.BigDecimal;

/**
 * Derives human-readable signal labels and crossover flags from raw indicator values.
 * Shared by StockQuoteController (compute path) and IndicatorSnapshotEntity (DB load path).
 */
public final class IndicatorSignals {

    private IndicatorSignals() {}

    public static SignalsDto buildSignals(BigDecimal close, BigDecimal rsi, BigDecimal adx,
                                          BigDecimal macdHistogram, SupertrendDto supertrend,
                                          BollingerBandsDto bb, BigDecimal vwap) {
        String rsiSig = rsi == null ? null
                : rsi.compareTo(BigDecimal.valueOf(70)) > 0 ? "OVERBOUGHT"
                : rsi.compareTo(BigDecimal.valueOf(30)) < 0 ? "OVERSOLD"
                : "NEUTRAL";

        String adxSig = adx == null ? null
                : adx.compareTo(BigDecimal.valueOf(25)) > 0 ? "TRENDING"
                : adx.compareTo(BigDecimal.valueOf(20)) < 0 ? "RANGING"
                : "DEVELOPING";

        String trendSig = (supertrend == null || supertrend.bullish() == null) ? null
                : supertrend.bullish() ? "BULLISH" : "BEARISH";

        String macdSig = macdHistogram == null ? null
                : macdHistogram.signum() > 0 ? "BULLISH" : "BEARISH";

        String bbPos = null;
        if (close != null && bb != null && bb.upper() != null && bb.lower() != null && bb.middle() != null) {
            if (close.compareTo(bb.upper()) > 0)       bbPos = "ABOVE_UPPER";
            else if (close.compareTo(bb.middle()) > 0) bbPos = "UPPER_HALF";
            else if (close.compareTo(bb.lower()) < 0)  bbPos = "BELOW_LOWER";
            else                                        bbPos = "LOWER_HALF";
        }

        String vwapPos = (close != null && vwap != null)
                ? (close.compareTo(vwap) >= 0 ? "ABOVE" : "BELOW") : null;

        return new SignalsDto(rsiSig, adxSig, trendSig, macdSig, bbPos, vwapPos);
    }

    public static CrossoversDto buildCrossovers(BigDecimal close, MovingAveragesDto ma) {
        if (ma == null) return null;
        return new CrossoversDto(
                ma.ema9()  != null && ma.ema20()  != null ? ma.ema9().compareTo(ma.ema20())   > 0 : null,
                ma.ema20() != null && ma.ema50()  != null ? ma.ema20().compareTo(ma.ema50())  > 0 : null,
                close      != null && ma.ema200() != null ? close.compareTo(ma.ema200())       > 0 : null,
                close      != null && ma.vwap()   != null ? close.compareTo(ma.vwap())         > 0 : null
        );
    }
}
