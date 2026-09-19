package com.angle.trading.bias;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory ring buffer of the most recent bias-change events across all
 * instruments — populated by {@link BiasChangeDetector}, read by the
 * dashboard's "What changed" panel.
 *
 * Not persisted; a JVM restart clears history. That's fine for a
 * dashboard widget — the user cares about "what's new since I last
 * looked," not "what changed last month."
 */
@Slf4j
@Component
public class BiasChangeHistory {

    private static final int MAX_ENTRIES = 25;

    /**
     * One recorded change event.
     *   at         — when it was observed
     *   symbol     — human name of the instrument
     *   changes    — list of change descriptions (same strings the alert used)
     *   score      — consolidated score at the time of the change
     */
    public record Entry(Instant at, String symbol, List<String> changes, int score) {}

    private final Deque<Entry> buffer = new ArrayDeque<>(MAX_ENTRIES);

    public synchronized void record(String symbol, List<String> changes, int score) {
        if (changes == null || changes.isEmpty()) return;
        buffer.addFirst(new Entry(Instant.now(), symbol, List.copyOf(changes), score));
        while (buffer.size() > MAX_ENTRIES) buffer.removeLast();
    }

    /** Snapshot of the current buffer, newest first. Never mutated. */
    public synchronized List<Entry> recent() {
        return new ArrayList<>(buffer);
    }

    /** Recent entries filtered by instrument symbol. */
    public synchronized List<Entry> recentFor(String symbol) {
        return buffer.stream()
                .filter(e -> symbol == null || symbol.equals(e.symbol()))
                .toList();
    }

    public synchronized int size() {
        return buffer.size();
    }
}
