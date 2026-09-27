package com.angle.trading.controller;

import com.angle.trading.config.NewsProperties;
import com.angle.trading.news.NewsBlackoutService;
import com.angle.trading.news.NewsCalendarService;
import com.angle.trading.news.NewsEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin UI + JSON API for the news blackout calendar.
 *
 *   GET  /admin/news                 — HTML page (active + upcoming + all)
 *   GET  /api/news/status            — snapshot (counts + last-loaded)
 *   GET  /api/news/active            — events blocking right now
 *   GET  /api/news/upcoming?days=7   — future events within N days
 *   GET  /api/news/all               — every event in calendar
 *   GET  /api/news/check?token=...   — dry-run: is this symbol blocked now?
 *   POST /api/news/reload            — re-read YAML file
 *   POST /api/news/enable?on=...     — master switch
 *   POST /api/news/severity?value=...— block threshold (LOW/MEDIUM/HIGH)
 */
@Controller
@RequiredArgsConstructor
public class NewsController {

    private final NewsCalendarService calendarService;
    private final NewsBlackoutService blackoutService;
    private final NewsProperties props;

    // ---------- HTML page ----------

    @GetMapping("/admin/news")
    public String page(Model model) {
        model.addAttribute("enabled",   props.isEnabled());
        model.addAttribute("severity",  props.getBlockSeverity());
        model.addAttribute("active",    calendarService.active());
        model.addAttribute("upcoming",  calendarService.upcoming(7));
        model.addAttribute("all",       calendarService.all());
        model.addAttribute("snapshot",  calendarService.snapshot());
        model.addAttribute("defaultBefore", props.getDefaultWindowBefore());
        model.addAttribute("defaultAfter",  props.getDefaultWindowAfter());
        return "admin/news";
    }

    // ---------- JSON API ----------

    @GetMapping("/api/news/status")
    @ResponseBody
    public NewsCalendarService.Snapshot status() {
        return calendarService.snapshot();
    }

    @GetMapping("/api/news/active")
    @ResponseBody
    public List<NewsEvent> active() {
        return calendarService.active();
    }

    @GetMapping("/api/news/upcoming")
    @ResponseBody
    public List<NewsEvent> upcoming(@RequestParam(defaultValue = "7") int days) {
        return calendarService.upcoming(days);
    }

    @GetMapping("/api/news/all")
    @ResponseBody
    public List<NewsEvent> all() {
        return calendarService.all();
    }

    @GetMapping("/api/news/check")
    @ResponseBody
    public Map<String, Object> check(@RequestParam String token) {
        NewsBlackoutService.Decision d = blackoutService.check(token);
        return Map.of(
                "token",   token,
                "allowed", d.allowed(),
                "reason",  d.reason()
        );
    }

    @PostMapping("/api/news/reload")
    @ResponseBody
    public Map<String, Object> reload() {
        calendarService.reload();
        return Map.of("ok", true, "snapshot", calendarService.snapshot());
    }

    @PostMapping("/api/news/enable")
    @ResponseBody
    public Map<String, Object> enable(@RequestParam boolean on) {
        props.setEnabled(on);
        return Map.of("ok", true, "enabled", on);
    }

    @PostMapping("/api/news/severity")
    @ResponseBody
    public Map<String, Object> severity(@RequestParam String value) {
        String v = value.toUpperCase();
        if (!v.matches("LOW|MEDIUM|HIGH")) {
            return Map.of("ok", false, "error", "severity must be LOW / MEDIUM / HIGH");
        }
        props.setBlockSeverity(v);
        return Map.of("ok", true, "blockSeverity", v);
    }
}
