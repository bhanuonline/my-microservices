package com.angle.trading.controller;

import com.angle.trading.paper.model.SessionStatus;
import com.angle.trading.paper.persistence.SessionEntity;
import com.angle.trading.paper.persistence.SessionRepository;
import com.angle.trading.paper.persistence.TradeEntity;
import com.angle.trading.paper.persistence.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only endpoints backed by the persistence layer.
 *
 * Whereas /api/paper/sessions reflects LIVE in-memory sessions (lost on restart),
 * these endpoints show the DURABLE history from the DB.
 *
 *   GET /api/paper/history                            → all past sessions, newest first
 *   GET /api/paper/history?status=COMPLETED           → filter by status
 *   GET /api/paper/history?strategy=ensemble          → filter by strategy name
 *   GET /api/paper/history/{id}                       → one session's metadata
 *   GET /api/paper/history/{id}/trades                → all trades in that session
 *   GET /api/paper/history/trades?page=0&size=50      → all trades across all sessions
 */
@RestController
@RequestMapping("/api/paper/history")
@RequiredArgsConstructor
public class PaperHistoryController {

    private final SessionRepository sessionRepo;
    private final TradeRepository   tradeRepo;

    @GetMapping
    public List<SessionEntity> listSessions(
            @RequestParam(required = false) SessionStatus status,
            @RequestParam(required = false) String strategy
    ) {
        if (status != null)   return sessionRepo.findByStatusOrderByStartedAtDesc(status);
        if (strategy != null) return sessionRepo.findByStrategyNameOrderByStartedAtDesc(strategy);
        return sessionRepo.findAllByOrderByStartedAtDesc();
    }

    @GetMapping("/{id}")
    public SessionEntity getSession(@PathVariable String id) {
        return sessionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No session with id: " + id));
    }

    @GetMapping("/{id}/trades")
    public List<TradeEntity> getTrades(@PathVariable String id) {
        return tradeRepo.findBySessionIdOrderByEntryTimeAsc(id);
    }

    @GetMapping("/trades")
    public List<TradeEntity> allTrades(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return tradeRepo.findAllByOrderByEntryTimeDesc(PageRequest.of(page, Math.min(size, 500)))
                .getContent();
    }
}
