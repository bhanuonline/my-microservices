package com.angle.trading.service;

import com.angle.trading.persistence.SignalEntity;
import com.angle.trading.persistence.SignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * CRUD wrapper around SignalEntity + business helpers for closing signals.
 *
 * All updates go through {@link #closeSignal} so the profit calc stays
 * consistent (BUY vs SELL sign flip is applied once, here).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalRepository repo;

    @Transactional
    public SignalEntity save(SignalEntity s) {
        if (s.getCreatedAt() == null) s.setCreatedAt(Instant.now());
        if (s.getOriginalStop() == null) s.setOriginalStop(s.getStop());
        return repo.save(s);
    }

    /**
     * Persist a trailing-stop tightening. Bumps trailUpdates counter for audit.
     * Called by LiveSignalMonitor when TrailingStopService returns a better stop.
     */
    @Transactional
    public void updateTrailingStop(SignalEntity s, BigDecimal newStop, BigDecimal highWaterMark) {
        if (!"OPEN".equals(s.getStatus())) return;
        BigDecimal old = s.getStop();
        s.setStop(newStop);
        s.setHighWaterMark(highWaterMark);
        s.setTrailUpdates(s.getTrailUpdates() + 1);
        repo.save(s);
        log.info("Signal {} TRAIL {} → {} (peak {}) · update #{}",
                s.getId(), old, newStop, highWaterMark, s.getTrailUpdates());
    }

    @Transactional(readOnly = true)
    public List<SignalEntity> findOpen() {
        return repo.findByStatus("OPEN");
    }

    @Transactional(readOnly = true)
    public List<SignalEntity> findOpenForToken(String symbolToken) {
        return repo.findByStatusAndSymbolToken("OPEN", symbolToken);
    }

    @Transactional(readOnly = true)
    public List<SignalEntity> findFiltered(Instant cutoff, String symbolToken, String status, String source) {
        return repo.findFiltered(cutoff, symbolToken, status, source);
    }

    /**
     * Dedupe check — has a similar signal (same symbol + action) already been
     * created within the last {@code minutes}? Prevents spamming duplicates.
     */
    @Transactional(readOnly = true)
    public boolean hasRecentSignal(String symbolToken, String action, int minutes) {
        return repo.countRecent(symbolToken, action, Instant.now().minusSeconds(minutes * 60L)) > 0;
    }

    /**
     * Mark a signal closed with an outcome (HIT_TARGET / HIT_STOP / EXPIRED).
     * Computes profit points + percent from entry and closedPrice.
     */
    @Transactional
    public void closeSignal(SignalEntity s, String newStatus, BigDecimal closedPrice) {
        if (!"OPEN".equals(s.getStatus())) return;   // already closed
        s.setStatus(newStatus);
        s.setClosedAt(Instant.now());
        s.setClosedPrice(closedPrice);

        // Profit calc — BUY earns when price rises, SELL earns when price falls
        BigDecimal diff = closedPrice.subtract(s.getEntry());
        if ("SELL".equalsIgnoreCase(s.getAction())) diff = diff.negate();
        s.setProfitPoints(diff.setScale(4, RoundingMode.HALF_UP));
        if (s.getEntry().signum() > 0) {
            s.setProfitPercent(diff.multiply(BigDecimal.valueOf(100))
                    .divide(s.getEntry(), 2, RoundingMode.HALF_UP));
        }
        repo.save(s);
        log.info("Signal {} CLOSED {} · {} → {} · {} pts ({}%)",
                s.getId(), newStatus, s.getEntry(), closedPrice,
                s.getProfitPoints(), s.getProfitPercent());
    }

    /** Auto-expire OPEN signals older than {@code cutoffHours}. Called by scheduled sweeper. */
    @Transactional
    public int expireStale(int cutoffHours) {
        Instant cutoff = Instant.now().minusSeconds(cutoffHours * 3600L);
        List<SignalEntity> open = repo.findByStatus("OPEN");
        int expired = 0;
        for (SignalEntity s : open) {
            if (s.getCreatedAt().isBefore(cutoff)) {
                // Expiring — no price hit; leave closedPrice null but set status.
                s.setStatus("EXPIRED");
                s.setClosedAt(Instant.now());
                repo.save(s);
                expired++;
            }
        }
        if (expired > 0) log.info("Signal service: expired {} stale signals older than {}h", expired, cutoffHours);
        return expired;
    }
}
