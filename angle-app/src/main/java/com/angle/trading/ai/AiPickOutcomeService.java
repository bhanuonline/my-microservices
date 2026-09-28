package com.angle.trading.ai;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.config.AiPickProperties;
import com.angle.trading.marketdata.MarketDataService;
import com.angle.trading.persistence.AiPickEntity;
import com.angle.trading.persistence.AiPickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Once per trading-day close, walk every OPEN AI pick and check whether
 * price hit the target or stop, or whether the horizon has elapsed.
 *
 *   HIT_TARGET  = day's HIGH crossed target       → return% = (target - entry) / entry
 *   HIT_STOP    = day's LOW crossed stop          → return% = (stop   - entry) / entry
 *   EXPIRED     = >= expiryDays old with no hit   → return% = (close  - entry) / entry
 *
 * Uses the same daily candles the picker uses. If a candle is missing the
 * pick is left OPEN for the next run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPickOutcomeService {

    private final AiPickProperties props;
    private final AiPickRepository repo;
    private final MarketDataService marketDataService;

    /** Manual trigger — same logic the cron uses. Returns count of closed picks. */
    @Transactional
    public int checkAll() {
        List<AiPickEntity> open = repo.findByStatus("OPEN");
        int closed = 0;
        for (AiPickEntity p : open) {
            try {
                if (checkOne(p)) closed++;
            } catch (Exception e) {
                log.debug("AI-pick outcome: {} — {}", p.getSymbol(), e.getMessage());
            }
        }
        if (closed > 0) log.info("AI-pick outcome: closed {} of {} open picks", closed, open.size());
        return closed;
    }

    @Scheduled(cron = "#{@aiPickProperties.outcomeCheckCron}", zone = "Asia/Kolkata")
    public void scheduledCheck() {
        if (!props.isEnabled()) return;
        checkAll();
    }

    private boolean checkOne(AiPickEntity p) {
        Exchange ex;
        try { ex = Exchange.valueOf(p.getExchange()); }
        catch (Exception e) { return false; }

        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(5, props.getExpiryDays() + 5));
        List<Candle> daily = marketDataService.getCandles("ANGEL", ex, p.getSymbolToken(), Interval.ONE_DAY, from, to);
        if (daily == null || daily.isEmpty()) return false;

        // Walk candles from pick date onward — first candle to hit target/stop wins.
        Instant pickAt = p.getCreatedAt();
        for (Candle c : daily) {
            Instant ct = c.timestamp();
            if (ct.isBefore(pickAt)) continue;

            if ("BUY".equalsIgnoreCase(p.getAction())) {
                if (p.getTargetPrice() != null && c.high().compareTo(p.getTargetPrice()) >= 0) {
                    close(p, "HIT_TARGET", p.getTargetPrice());
                    return true;
                }
                if (p.getStopLoss() != null && c.low().compareTo(p.getStopLoss()) <= 0) {
                    close(p, "HIT_STOP", p.getStopLoss());
                    return true;
                }
            }
        }

        // No hit yet — check expiry
        if (Duration.between(pickAt, Instant.now()).toDays() >= props.getExpiryDays()) {
            BigDecimal lastClose = daily.get(daily.size() - 1).close();
            close(p, "EXPIRED", lastClose);
            return true;
        }
        return false;
    }

    private void close(AiPickEntity p, String status, BigDecimal closedPrice) {
        p.setStatus(status);
        p.setClosedAt(Instant.now());
        p.setClosedPrice(closedPrice);
        if (p.getEntryPrice() != null && p.getEntryPrice().signum() > 0) {
            BigDecimal ret = closedPrice.subtract(p.getEntryPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(p.getEntryPrice(), 2, RoundingMode.HALF_UP);
            p.setReturnPercent(ret);
        }
        repo.save(p);
        log.info("AI-pick {} {} · {} → {} · {}%",
                p.getId(), status, p.getEntryPrice(), closedPrice, p.getReturnPercent());
    }
}
