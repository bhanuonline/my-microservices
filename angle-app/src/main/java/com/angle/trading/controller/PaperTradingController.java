package com.angle.trading.controller;

import com.angle.trading.paper.PaperTradingSessionManager;
import com.angle.trading.paper.model.CreateSessionRequest;
import com.angle.trading.paper.model.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Paper-trading session lifecycle.
 *
 *   POST /api/paper/sessions
 *     Body: { "strategyName": "ob-retest", "sourceType": "replay-nifty-csv", "candlesPerSecond": 50 }
 *     Returns: SessionSnapshot (contains sessionId).
 *
 *   GET  /api/paper/sessions
 *     All sessions in this JVM.
 *
 *   GET  /api/paper/sessions/{id}
 *     Snapshot of one session — poll this to watch trades happen live.
 *
 *   POST /api/paper/sessions/{id}/stop
 *     Force-stops a session and closes any open position at the last price.
 */
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperTradingController {

    private final PaperTradingSessionManager manager;

    @PostMapping("/sessions")
    public SessionSnapshot create(@RequestBody CreateSessionRequest req) {
        return manager.create(req);
    }

    @GetMapping("/sessions")
    public List<SessionSnapshot> list() {
        return manager.list();
    }

    @GetMapping("/sessions/{id}")
    public SessionSnapshot get(@PathVariable String id) {
        return manager.get(id);
    }

    @PostMapping("/sessions/{id}/stop")
    public SessionSnapshot stop(@PathVariable String id) {
        return manager.stop(id);
    }
}
