package com.angle.trading.dto;

/**
 * Tracks the completion state of one timeframe's candle bars.
 * Calculated purely from current time — no server state needed.
 *
 * <p>justCompleted is true when a new bar has just closed in the
 * current base-interval window. Use this to trigger alerts:
 * <pre>
 *   if (tf.justCompleted()) notifyUser("1h bar just closed!");
 * </pre>
 *
 * @param completeBars   number of complete bars since market open today
 * @param justCompleted  true if this TF's bar closed in the last base interval
 * @param latestCandleAt IST time of the latest complete bar e.g. "10:15 IST"
 * @param nextBarAt      IST time when the next bar will close e.g. "11:15 IST"
 */
public record TimeframeStatusDto(
        int     completeBars,
        boolean justCompleted,
        String  latestCandleAt,
        String  nextBarAt
) {}
