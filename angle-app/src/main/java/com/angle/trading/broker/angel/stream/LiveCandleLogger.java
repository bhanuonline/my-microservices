package com.angle.trading.broker.angel.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints every closed candle from the live stream — proof the aggregator works.
 *
 * Any other component can listen to CandleClosedEvent the same way:
 *   • persist to candle_history table
 *   • re-run strategy on the latest bar
 *   • push to browser dashboard
 *   • fire alerts
 */
@Slf4j
@Component
public class LiveCandleLogger {

    @EventListener
    public void onCandleClosed(CandleClosedEvent e) {
        log.info("LiveCandle {} {} → O={} H={} L={} C={} @ {}",
                e.symbolToken(),
                e.interval(),
                e.candle().open(),
                e.candle().high(),
                e.candle().low(),
                e.candle().close(),
                e.candle().timestamp());
    }
}
