package com.angle.trading.service;

import com.angle.trading.broker.angel.stream.TickEvent;
import com.angle.trading.broker.angel.stream.model.Tick;
import com.angle.trading.config.SignalsProperties;
import com.angle.trading.persistence.SignalEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Watches every live tick and closes OPEN signals when target / stop is hit.
 *
 * Runs entirely in-memory except for the actual DB write when a signal closes
 * (rare event vs tick rate). Uses SignalService for the state transition so
 * profit math stays consistent.
 *
 * Also runs a scheduled sweeper to EXPIRE stale OPEN signals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSignalMonitor {

    private final SignalsProperties props;
    private final SignalService signalService;
    private final TrailingStopService trailingStopService;

    // ---------- on tick ----------

    @EventListener
    public void onTick(TickEvent e) {
        if (!props.getMonitor().isEnabled()) return;
        Tick t = e.tick();
        BigDecimal ltp = t.ltp();
        if (ltp == null) return;

        List<SignalEntity> open = signalService.findOpenForToken(t.symbolToken());
        if (open.isEmpty()) return;

        for (SignalEntity s : open) {
            // 1. Trail the stop first (may tighten before the hit check).
            //    computeNewStop also updates highWaterMark on the entity in-place.
            if (!trailingStopService.isDisabled()) {
                BigDecimal newStop = trailingStopService.computeNewStop(s, ltp);
                if (newStop != null) {
                    signalService.updateTrailingStop(s, newStop, s.getHighWaterMark());
                }
            }
            // 2. Now check hits against the (possibly tightened) stop / target.
            String hit = checkHit(s, ltp);
            if (hit != null) {
                signalService.closeSignal(s, hit, ltp);
            }
        }
    }

    /** Returns "HIT_TARGET" / "HIT_STOP" if the LTP crossed either level, else null. */
    private static String checkHit(SignalEntity s, BigDecimal ltp) {
        if ("BUY".equalsIgnoreCase(s.getAction())) {
            if (ltp.compareTo(s.getTarget()) >= 0) return "HIT_TARGET";
            if (ltp.compareTo(s.getStop())   <= 0) return "HIT_STOP";
        } else if ("SELL".equalsIgnoreCase(s.getAction())) {
            if (ltp.compareTo(s.getTarget()) <= 0) return "HIT_TARGET";
            if (ltp.compareTo(s.getStop())   >= 0) return "HIT_STOP";
        }
        return null;
    }

    // ---------- expiry sweeper ----------

    @Scheduled(fixedRateString = "#{@signalsProperties.monitor.expirySweepMinutes * 60 * 1000}")
    public void sweepExpired() {
        if (!props.getMonitor().isEnabled()) return;
        signalService.expireStale(props.getMonitor().getExpiryHours());
    }
}
