package com.angle.trading.bias;

import com.angle.trading.alerts.AlertService;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.bias.model.TimeframeBias;
import com.angle.trading.config.BiasProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every N minutes:
 *   1. Rebuild the bias sheet for each configured instrument
 *   2. Compare with the previous snapshot
 *   3. If a significant change is detected, fire an alert
 *
 * Change types (each configurable via bias.change-alerts.*):
 *   - onBiasFlip:            any multi-TF timeframe bias changed direction
 *   - onScoreJump:           total score moved by >= threshold
 *   - onNewStructuralEvent:  a new BOS or CHoCH appeared
 *   - onNewSweep:            a new liquidity sweep detected
 *   - onAdxCross:            ADX crossed the 25 threshold (either direction)
 *
 * Uses the fixed-delay scheduler at rate = bias.refresh-minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiasChangeDetector {

    private final BiasProperties biasProperties;
    private final BiasSheetService biasSheetService;
    private final AlertService alertService;

    /** symbolToken → previous snapshot */
    private final Map<String, BiasSheet> lastSnapshot = new ConcurrentHashMap<>();

    /**
     * Runs at the configured refresh interval. Cron uses minute-precision:
     * for bias.refresh-minutes=15, we run every 15 minutes past the hour.
     */
    @Scheduled(fixedRateString = "#{@biasProperties.refreshMinutes * 60 * 1000}")
    public void tick() {
        if (!biasProperties.isEnabled()) return;
        if (!biasProperties.getChangeAlerts().isEnabled()) return;

        List<BiasProperties.Instrument> instruments = biasProperties.getInstruments();
        if (instruments.isEmpty()) return;

        for (BiasProperties.Instrument cfg : instruments) {
            try {
                BiasSheet current = biasSheetService.build(cfg);
                BiasSheet previous = lastSnapshot.put(cfg.getSymbolToken(), current);
                if (previous == null) {
                    log.debug("Baseline snapshot for {} — no alerts on first run", cfg.getSymbol());
                    continue;
                }
                List<String> changes = detectSignificantChanges(previous, current);
                if (!changes.isEmpty()) {
                    fireAlert(cfg, current, changes);
                }
            } catch (Exception e) {
                log.warn("Bias change check failed for {}: {}", cfg.getSymbol(), e.getMessage());
            }
        }
    }

    private List<String> detectSignificantChanges(BiasSheet prev, BiasSheet now) {
        BiasProperties.ChangeAlerts triggers = biasProperties.getChangeAlerts();
        List<String> changes = new ArrayList<>();

        // 1. Multi-TF bias flips
        if (triggers.isOnBiasFlip()) {
            for (int i = 0; i < now.multiTfBias().size() && i < prev.multiTfBias().size(); i++) {
                TimeframeBias p = prev.multiTfBias().get(i);
                TimeframeBias n = now.multiTfBias().get(i);
                if (p.bias() != n.bias() && (p.bias() != null || n.bias() != null)) {
                    changes.add(String.format("%s bias %s → %s",
                            n.interval(),
                            p.bias() == null ? "—" : p.bias().name(),
                            n.bias() == null ? "—" : n.bias().name()));
                }
            }
        }

        // 2. Consolidated score jump
        if (triggers.isOnScoreJump()) {
            int diff = Math.abs(now.consolidated().totalScore() - prev.consolidated().totalScore());
            if (diff >= triggers.getScoreJumpThreshold()) {
                changes.add(String.format("Score jumped %+d → %+d (Δ%d)",
                        prev.consolidated().totalScore(),
                        now.consolidated().totalScore(),
                        diff));
            }
            // Recommendation flip is always alert-worthy
            if (!prev.consolidated().recommendation().equals(now.consolidated().recommendation())) {
                changes.add(String.format("Recommendation %s → %s",
                        prev.consolidated().recommendation(),
                        now.consolidated().recommendation()));
            }
        }

        // 3. New structural event
        if (triggers.isOnNewStructuralEvent()) {
            var prevEvent = prev.structure().lastEvent();
            var nowEvent  = now.structure().lastEvent();
            if (nowEvent != null && (prevEvent == null || prevEvent.index() != nowEvent.index())) {
                changes.add(String.format("New %s %s @ %s",
                        nowEvent.type(), nowEvent.direction(), nowEvent.brokenLevel()));
            }
        }

        // 4. New liquidity sweep
        if (triggers.isOnNewSweep()) {
            int prevSweepCount = prev.zones().recentSweeps().size();
            int nowSweepCount  = now.zones().recentSweeps().size();
            if (nowSweepCount > prevSweepCount) {
                var latestSweep = now.zones().recentSweeps().get(nowSweepCount - 1);
                changes.add(String.format("Sweep %s @ %s",
                        latestSweep.side(), latestSweep.levelPrice()));
            }
        }

        // 5. ADX crossed 25 threshold
        if (triggers.isOnAdxCross()) {
            var pAdx = prev.trendFilters().adx14();
            var nAdx = now.trendFilters().adx14();
            if (pAdx != null && nAdx != null) {
                boolean pStrong = pAdx.doubleValue() >= 25;
                boolean nStrong = nAdx.doubleValue() >= 25;
                if (pStrong != nStrong) {
                    changes.add(String.format("ADX %s 25 (was %.1f, now %.1f)",
                            nStrong ? "↑ above" : "↓ below",
                            pAdx.doubleValue(), nAdx.doubleValue()));
                }
            }
        }
        return changes;
    }

    private void fireAlert(BiasProperties.Instrument cfg, BiasSheet current, List<String> changes) {
        StringBuilder text = new StringBuilder("📊 BIAS CHANGE — ").append(cfg.getSymbol()).append("\n\n");
        for (String c : changes) text.append("• ").append(c).append("\n");
        text.append("\nCurrent: ").append(current.consolidated().recommendation())
                .append(" (").append(current.consolidated().strength())
                .append(", score ").append(current.consolidated().totalScore()).append(")");
        alertService.notifyError("bias-change:" + cfg.getSymbol(), text.toString());
        log.info("Bias change alert fired for {}: {} changes", cfg.getSymbol(), changes.size());
    }
}
