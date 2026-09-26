package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.stream.model.Tick;

/**
 * Spring ApplicationEvent wrapping a single Angel tick.
 *
 * Any bean can subscribe with:
 *   {@code @EventListener public void onTick(TickEvent e) { ... } }
 *
 * Add {@code @Async} on the listener to consume off the event-publisher thread
 * (recommended for anything that does IO — otherwise you block the WS reader).
 */
public record TickEvent(Tick tick) {}
