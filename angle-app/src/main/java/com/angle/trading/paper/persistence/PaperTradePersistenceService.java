package com.angle.trading.paper.persistence;

import com.angle.trading.paper.model.SessionSnapshot;
import com.angle.trading.strategy.model.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Writes paper-trading data to the DB.
 *
 * All methods are best-effort: exceptions are logged but not re-thrown so
 * a DB blip never kills a live trading session. If a save fails, the trade
 * is still in the in-memory book and the log — we just lose the persistent
 * copy for that one event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradePersistenceService {

    private final SessionRepository sessionRepo;
    private final TradeRepository   tradeRepo;

    @Transactional
    public void saveNewSession(SessionSnapshot snap) {
        try {
            sessionRepo.save(SessionEntity.from(snap));
            log.debug("Persisted new session {}", snap.sessionId());
        } catch (Exception e) {
            log.warn("Failed to persist new session {}: {}", snap.sessionId(), e.getMessage());
        }
    }

    @Transactional
    public void saveTrade(String sessionId, Trade trade) {
        try {
            tradeRepo.save(TradeEntity.from(sessionId, trade));
            log.debug("Persisted trade for session {}: pnl={}", sessionId, trade.pnl());
        } catch (Exception e) {
            log.warn("Failed to persist trade for session {}: {}", sessionId, e.getMessage());
        }
    }

    @Transactional
    public void updateSession(SessionSnapshot snap) {
        try {
            Optional<SessionEntity> existing = sessionRepo.findById(snap.sessionId());
            if (existing.isEmpty()) {
                // Session wasn't saved at start — persist it now as a full record.
                sessionRepo.save(SessionEntity.from(snap));
                return;
            }
            SessionEntity e = existing.get();
            e.updateFrom(snap);
            sessionRepo.save(e);
        } catch (Exception e) {
            log.warn("Failed to update session {}: {}", snap.sessionId(), e.getMessage());
        }
    }
}
