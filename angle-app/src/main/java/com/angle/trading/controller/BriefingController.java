package com.angle.trading.controller;

import com.angle.trading.briefing.BriefingService;
import com.angle.trading.persistence.BriefingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Pre-market briefing page + REST API.
 *
 *   GET  /briefing                    → latest briefing (HTML)
 *   GET  /briefing/history            → 30-day history (HTML)
 *   GET  /api/briefing/latest         → latest as JSON
 *   POST /api/briefing/generate       → force generation now
 */
@Controller
@RequiredArgsConstructor
public class BriefingController {

    private final BriefingService service;

    @GetMapping("/briefing")
    public String page(Model model) {
        BriefingEntity latest = service.latest();
        model.addAttribute("latest",  latest);
        model.addAttribute("history", service.history());
        return "briefing/index";
    }

    @GetMapping("/api/briefing/latest")
    @ResponseBody
    public BriefingEntity latest() {
        return service.latest();
    }

    @GetMapping("/api/briefing/history")
    @ResponseBody
    public List<BriefingEntity> history() {
        return service.history();
    }

    @PostMapping("/api/briefing/generate")
    @ResponseBody
    public Map<String, Object> generate() {
        BriefingEntity b = service.generate(LocalDate.now(ZoneId.of("Asia/Kolkata")));
        return Map.of("ok", true, "id", b.getId(),
                "forDate", b.getForDate().toString(),
                "bias", b.getBias());
    }
}
