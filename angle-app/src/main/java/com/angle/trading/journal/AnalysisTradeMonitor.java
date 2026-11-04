package com.angle.trading.journal;

import com.angle.trading.broker.angel.AngelLtpClient;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Checks open trades every 5 minutes during market hours.
 * Auto-closes when price hits Target 1, Target 2, or Stop Loss.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisTradeMonitor {

    private final AnalysisTradeService       tradeService;
    private final InstrumentMasterService    instruments;
    private final AngelLtpClient             ltpClient;

    /** Runs every 5 minutes. */
    @Scheduled(fixedDelayString = "PT5M")
    public void checkOpenTrades() {
        List<AnalysisTradeJournalEntity> open = tradeService.findOpenEntities();
        if (open.isEmpty()) return;

        log.debug("[TradeMonitor] Checking {} open trades", open.size());
        for (AnalysisTradeJournalEntity trade : open) {
            try {
                checkTrade(trade);
            } catch (Exception e) {
                log.warn("[TradeMonitor] Error checking trade {} {}: {}", trade.getId(), trade.getSymbol(), e.getMessage());
            }
        }
    }

    private void checkTrade(AnalysisTradeJournalEntity trade) {
        Optional<Instrument> inst = instruments.findBySymbol(trade.getSymbol());
        if (inst.isEmpty()) return;

        Instrument i = inst.get();
        BigDecimal ltp = ltpClient.fetchLtp(i.exchSeg(), trade.getSymbol(), i.token()).orElse(null);
        if (ltp == null) return;

        boolean isLong = "LONG".equals(trade.getDirection());

        // Check Target 2 first (better outcome)
        if (trade.getTarget2() != null) {
            if (isLong  && ltp.compareTo(trade.getTarget2()) >= 0) { close(trade, ltp, "TARGET2_HIT"); return; }
            if (!isLong && ltp.compareTo(trade.getTarget2()) <= 0) { close(trade, ltp, "TARGET2_HIT"); return; }
        }

        // Check Target 1
        if (trade.getTarget1() != null) {
            if (isLong  && ltp.compareTo(trade.getTarget1()) >= 0) { close(trade, ltp, "TARGET1_HIT"); return; }
            if (!isLong && ltp.compareTo(trade.getTarget1()) <= 0) { close(trade, ltp, "TARGET1_HIT"); return; }
        }

        // Check Stop Loss
        if (trade.getStopLoss() != null) {
            if (isLong  && ltp.compareTo(trade.getStopLoss()) <= 0) { close(trade, ltp, "STOP_HIT"); return; }
            if (!isLong && ltp.compareTo(trade.getStopLoss()) >= 0) { close(trade, ltp, "STOP_HIT"); }
        }
    }

    private void close(AnalysisTradeJournalEntity trade, BigDecimal ltp, String reason) {
        tradeService.autoClose(trade.getId(), ltp, reason);
    }
}
