package com.angle.trading.controller;

import com.angle.trading.broker.angel.stream.LiveStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

/**
 * Endpoints for live browser push.
 *
 *   GET /api/live/stream                → SSE stream of ALL ticks + candles
 *   GET /api/live/stream?token=99926000 → filtered to one instrument
 *   GET /api/live/stream?token=X&token=Y → filtered to N instruments
 *   GET /live/ticker                     → demo HTML page that consumes the stream
 *
 * SSE endpoint returns text/event-stream with two named events:
 *   event: tick    → { token, ltp, ts }
 *   event: candle  → { token, interval, open, high, low, close, ts }
 *
 * Client (browser) example:
 *   const es = new EventSource('/api/live/stream?token=99926000');
 *   es.addEventListener('tick',   e => console.log(JSON.parse(e.data)));
 *   es.addEventListener('candle', e => console.log(JSON.parse(e.data)));
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveStreamService;

    @GetMapping(value = "/api/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(name = "token", required = false) List<String> tokens) {
        Set<String> filter = tokens == null ? Set.of() : Set.copyOf(tokens);
        return liveStreamService.subscribe(filter);
    }

    /** Demo page: live-updating instrument grid backed by the SSE stream. */
    @GetMapping("/live/ticker")
    public String ticker(Model model) {
        return "live/ticker";
    }
}
