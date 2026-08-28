package com.angle.trading.paper;

import com.angle.trading.config.PaperAutostartProperties;
import com.angle.trading.paper.model.CreateSessionRequest;
import com.angle.trading.paper.model.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Fires configured paper-trading sessions after the app is fully up.
 *
 * Runs on {@link ApplicationReadyEvent} — later than @PostConstruct so all
 * beans (including StrategyRegistry and Angel auth) are ready.
 *
 * Each session is created via the same code path as a manual POST, so
 * config validation errors show up here just like from the REST endpoint.
 * If one session fails to start, the loop continues with the next — one
 * bad config doesn't take down the app.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperAutostartService {

    private final PaperAutostartProperties  props;
    private final PaperTradingSessionManager manager;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.isEnabled()) {
            log.info("Paper auto-start disabled (paper.autostart.enabled=false)");
            return;
        }
        if (props.getSessions().isEmpty()) {
            log.info("Paper auto-start enabled but no sessions configured");
            return;
        }

        log.info("Auto-starting {} paper session(s)", props.getSessions().size());
        int ok = 0, failed = 0;
        for (int i = 0; i < props.getSessions().size(); i++) {
            PaperAutostartProperties.Session cfg = props.getSessions().get(i);
            try {
                CreateSessionRequest req = new CreateSessionRequest(
                        cfg.getStrategyName(),
                        cfg.getSourceType(),
                        cfg.getCandlesPerSecond(),
                        cfg.getSymbolToken(),
                        cfg.getExchange(),
                        cfg.getInterval(),
                        cfg.getWarmupCandles(),
                        cfg.getPollIntervalSeconds()
                );
                SessionSnapshot snap = manager.create(req);
                log.info("Auto-start #{} OK — sessionId={} strategy={} source={}",
                        i, snap.sessionId(), snap.strategyName(), snap.sourceName());
                ok++;
            } catch (Exception e) {
                log.error("Auto-start #{} FAILED (strategy={}, sourceType={}): {}",
                        i, cfg.getStrategyName(), cfg.getSourceType(), e.getMessage());
                failed++;
            }
        }
        log.info("Paper auto-start complete: {} ok, {} failed", ok, failed);
    }
}
