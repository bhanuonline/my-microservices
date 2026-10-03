package com.angle.trading.controller;

import com.angle.trading.config.NewsFeedProperties;
import com.angle.trading.news.NewsHeadlineService;
import com.angle.trading.persistence.NewsHeadlineEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * News headlines page + REST API.
 *
 *   GET  /news                          → HTML feed page
 *   GET  /api/news/headlines            → JSON list
 *   POST /api/news/refresh              → force fetch now
 *   POST /api/news/tag                  → force AI-tagging pass
 */
@Controller
@RequiredArgsConstructor
public class NewsHeadlineController {

    private final NewsHeadlineService service;
    private final NewsFeedProperties  props;

    @GetMapping("/news")
    public String page(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int limit,
            Model model) {

        List<NewsHeadlineEntity> headlines = service.findRecent(hours, source, sentiment, sector, limit);
        long total24h = service.countLast24h();
        long bullish  = headlines.stream().filter(h -> "BULLISH".equals(h.getSentiment())).count();
        long bearish  = headlines.stream().filter(h -> "BEARISH".equals(h.getSentiment())).count();
        long neutral  = headlines.stream().filter(h -> "NEUTRAL".equals(h.getSentiment())).count();

        model.addAttribute("headlines",      headlines);
        model.addAttribute("selSource",      source == null ? "" : source);
        model.addAttribute("selSentiment",   sentiment == null ? "" : sentiment);
        model.addAttribute("selSector",      sector == null ? "" : sector);
        model.addAttribute("selHours",       hours);
        model.addAttribute("total24h",       total24h);
        model.addAttribute("bullish",        bullish);
        model.addAttribute("bearish",        bearish);
        model.addAttribute("neutral",        neutral);
        model.addAttribute("sources",        props.getSources());
        model.addAttribute("refreshSeconds", props.getPageRefreshSeconds());
        model.addAttribute("enabled",        props.isEnabled());
        return "news/headlines";
    }

    @GetMapping("/api/news/headlines")
    @ResponseBody
    public List<NewsHeadlineEntity> apiHeadlines(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String sentiment,
            @RequestParam(required = false) String sector,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int limit) {
        return service.findRecent(hours, source, sentiment, sector, limit);
    }

    @PostMapping("/api/news/refresh")
    @ResponseBody
    public Map<String, Object> refresh() {
        int fetched = service.refreshAll();
        int tagged  = service.tagUntagged();
        return Map.of("ok", true, "inserted", fetched, "tagged", tagged);
    }

    @PostMapping("/api/news/tag")
    @ResponseBody
    public Map<String, Object> tag() {
        int n = service.tagUntagged();
        return Map.of("ok", true, "tagged", n);
    }
}
