package com.angle.trading.candle;

import com.angle.trading.bias.MarketCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Scheduled triggers for CandleJobService.
 *
 * Three automatic schedules (controlled by candle.history-job.*):
 *   pre-market  09:00 AM Mon-Fri → intraday (5m/15m/1h) for all instruments
 *   post-market 15:40 PM Mon-Fri → daily (1D) for all instruments
 *   weekend     Saturday 10:00   → 1D backfill (730-day) for all instruments
 *
 * Plus manual triggers via CandleJobController API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleHistoryJob {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CandleJobService    service;
    private final CandleJobProperties props;
    private final MarketCalendar      marketCalendar;

    // ── Scheduled triggers ────────────────────────────────────────────

    @Scheduled(cron = "#{@candleJobProperties.preMarketCron}",
               zone  = "#{@candleJobProperties.zone}")
    public void preMarket() {
        if (!props.isEnabled()) return;
        if (!marketCalendar.isMarketOpen(LocalDate.now(IST))) {
            log.info("CandleJob preMarket SKIP — market holiday");
            return;
        }
        log.info("CandleJob preMarket triggered (SCHEDULED)");
        service.runAll("SCHEDULED", "SYSTEM", "INTRADAY",
                props.getIntradayLookbackDays(), null, null);
    }

    @Scheduled(cron = "#{@candleJobProperties.postMarketCron}",
               zone  = "#{@candleJobProperties.zone}")
    public void postMarket() {
        if (!props.isEnabled()) return;
        if (!marketCalendar.isMarketOpen(LocalDate.now(IST))) {
            log.info("CandleJob postMarket SKIP — market holiday");
            return;
        }
        log.info("CandleJob postMarket triggered (SCHEDULED)");
        service.runAll("SCHEDULED", "SYSTEM", "1D",
                props.getDailyLookbackDays(), null, null);
    }

    @Scheduled(cron = "#{@candleJobProperties.weekendCron}",
               zone  = "#{@candleJobProperties.zone}")
    public void weekendBackfill() {
        if (!props.isEnabled()) return;
        log.info("CandleJob weekend backfill triggered (SCHEDULED)");
        service.runAll("SCHEDULED", "SYSTEM", "1D",
                props.getDailyLookbackDays(), null, null);
    }

    // ── Startup trigger ───────────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.isEnabled() || !props.isRunOnStartup()) return;
        log.info("CandleJob startup run triggered");
        service.runAll("STARTUP", "SYSTEM", null, null, null, null);
    }
}
