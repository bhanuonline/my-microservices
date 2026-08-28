package com.angle.trading.paper.source;

import com.angle.trading.broker.model.Candle;

import java.util.function.Consumer;

/**
 * Pushes candles into the paper-trading pipeline.
 *
 * Different implementations plug in different origins:
 *   HistoricalReplayCandleSource — replays a fixed candle list at speed
 *   (later) AngelLiveCandleSource — polls Angel for the current bar
 *
 * The source is passive from the caller's point of view: you register
 * a callback via {@link #start} and get pushed candles until you call
 * {@link #stop} or the source runs out (in which case it calls
 * onComplete once).
 */
public interface CandleSource {

    /** Short label used in logs and API responses. */
    String name();

    /**
     * Begin pushing candles.
     * @param onCandle   called once per candle emitted
     * @param onComplete called once when the source is finished (no more candles coming)
     */
    void start(Consumer<Candle> onCandle, Runnable onComplete);

    /** Stop pushing candles immediately. Idempotent. */
    void stop();
}
