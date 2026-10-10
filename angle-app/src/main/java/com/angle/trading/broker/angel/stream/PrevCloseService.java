package com.angle.trading.broker.angel.stream;

import com.angle.trading.broker.angel.AngelLtpClient;
import com.angle.trading.config.BrokerProperties;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of previous-day close prices, used to compute
 * "today's change" on live ticks (what every broker shows: "+120.45  (+0.49%)").
 *
 * Populated on app start (one Angel LTP call per enabled instrument) and
 * refreshed every weekday at 09:00 IST — a few minutes before the regular
 * session opens so by the first tick the cache is already warm.
 *
 * Missing or stale entries degrade silently: callers get Optional.empty() and
 * the UI just hides the change line for that token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrevCloseService {

    private final AngelLtpClient ltpClient;
    private final InstrumentService instrumentService;
    private final BrokerProperties brokerProperties;

    private final ConcurrentHashMap<String, BigDecimal> prevCloseByToken = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!brokerProperties.getAngel().isEnabled()) {
            log.info("PrevCloseService: Angel disabled, skipping prev-close warmup");
            return;
        }
        refreshAll();
    }

    /** Every weekday at 09:00 IST — just before market open. */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Kolkata")
    public void scheduledRefresh() {
        if (!brokerProperties.getAngel().isEnabled()) return;
        refreshAll();
    }

    private void refreshAll() {
        List<BiasInstrumentEntity> enabled = instrumentService.listEnabled();
        int ok = 0, fail = 0;
        for (BiasInstrumentEntity ins : enabled) {
            Optional<BigDecimal> close = ltpClient.fetchPreviousClose(
                    ins.getExchange(), ins.getSymbol(), ins.getSymbolToken());
            if (close.isPresent()) {
                prevCloseByToken.put(ins.getSymbolToken(), close.get());
                ok++;
            } else {
                fail++;
            }
        }
        log.info("PrevCloseService: refreshed {} prev-close values ({} failed)", ok, fail);
    }

    /** Previous day close for the token, or empty if we don't have one yet. */
    public Optional<BigDecimal> prevClose(String symbolToken) {
        return Optional.ofNullable(prevCloseByToken.get(symbolToken));
    }

    /** LTP - prevClose, or empty if no baseline. */
    public Optional<BigDecimal> change(String symbolToken, BigDecimal ltp) {
        return prevClose(symbolToken).map(ltp::subtract);
    }

    /** (LTP - prevClose) / prevClose * 100, 2dp, or empty. */
    public Optional<BigDecimal> changePercent(String symbolToken, BigDecimal ltp) {
        return prevClose(symbolToken).map(prev -> {
            if (prev.signum() == 0) return BigDecimal.ZERO;
            return ltp.subtract(prev)
                      .multiply(BigDecimal.valueOf(100))
                      .divide(prev, 2, RoundingMode.HALF_UP);
        });
    }
}
