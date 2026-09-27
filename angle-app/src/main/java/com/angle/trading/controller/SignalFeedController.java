package com.angle.trading.controller;

import com.angle.trading.config.SignalsProperties;
import com.angle.trading.persistence.SignalEntity;
import com.angle.trading.service.InstrumentService;
import com.angle.trading.service.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;

/**
 * HTML feed page + JSON API for the signal lifecycle.
 *
 *   GET /signals                              → HTML page with cards
 *   GET /api/signals/feed?...                 → JSON list for auto-refresh polling
 *
 * Query params (both endpoints):
 *   token    — filter to one instrument (default: all)
 *   status   — OPEN / HIT_TARGET / HIT_STOP / EXPIRED / (blank = all)
 *   hours    — max age in hours (default from config)
 */
@Controller
@RequiredArgsConstructor
public class SignalFeedController {

    private final SignalService signalService;
    private final SignalsProperties props;
    private final InstrumentService instrumentService;

    @GetMapping("/signals")
    public String page(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer hours,
            Model model
    ) {
        int hrs = hours != null ? hours : props.getFeed().getDefaultMaxAgeHours();
        Instant cutoff = Instant.now().minusSeconds(hrs * 3600L);
        List<SignalEntity> signals = signalService.findFiltered(
                cutoff,
                emptyToNull(token),
                emptyToNull(status),
                emptyToNull(source));

        // Simple stats for the header
        long open = signals.stream().filter(s -> "OPEN".equals(s.getStatus())).count();
        long won  = signals.stream().filter(s -> "HIT_TARGET".equals(s.getStatus())).count();
        long lost = signals.stream().filter(s -> "HIT_STOP".equals(s.getStatus())).count();
        long exp  = signals.stream().filter(s -> "EXPIRED".equals(s.getStatus())).count();

        // AI-agreement stat: of all AI signals in view, what % match a CONSENSUS
        // signal for the same symbol+action within a 3-minute window (they fire
        // near-simultaneously so a short window is enough).
        long aiTotal = signals.stream().filter(s -> "AI".equals(s.getSource())).count();
        long aiAgree = signals.stream().filter(s -> "AI".equals(s.getSource()))
                .filter(ai -> signals.stream()
                        .filter(c -> "CONSENSUS".equals(c.getSource()))
                        .filter(c -> c.getSymbolToken().equals(ai.getSymbolToken()))
                        .filter(c -> c.getAction().equalsIgnoreCase(ai.getAction()))
                        .anyMatch(c -> Math.abs(c.getCreatedAt().getEpochSecond() - ai.getCreatedAt().getEpochSecond()) < 180))
                .count();

        model.addAttribute("signals",     signals);
        model.addAttribute("instruments", instrumentService.listEnabled());
        model.addAttribute("selToken",    token == null ? "" : token);
        model.addAttribute("selStatus",   status == null ? "" : status);
        model.addAttribute("selSource",   source == null ? "" : source);
        model.addAttribute("hours",       hrs);
        model.addAttribute("autoRefresh", props.getFeed().getAutoRefreshSeconds());
        model.addAttribute("statsOpen",   open);
        model.addAttribute("statsWon",    won);
        model.addAttribute("statsLost",   lost);
        model.addAttribute("statsExp",    exp);
        model.addAttribute("winRate",     (won + lost) == 0 ? 0 : Math.round(100.0 * won / (won + lost)));
        model.addAttribute("aiTotal",     aiTotal);
        model.addAttribute("aiAgreeRate", aiTotal == 0 ? 0 : Math.round(100.0 * aiAgree / aiTotal));

        return "signals/feed";
    }

    @GetMapping("/api/signals/feed")
    @ResponseBody
    public List<SignalEntity> apiFeed(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer hours
    ) {
        int hrs = hours != null ? hours : props.getFeed().getDefaultMaxAgeHours();
        return signalService.findFiltered(
                Instant.now().minusSeconds(hrs * 3600L),
                emptyToNull(token),
                emptyToNull(status),
                emptyToNull(source));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
