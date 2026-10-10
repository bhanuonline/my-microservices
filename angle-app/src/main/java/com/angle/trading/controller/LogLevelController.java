package com.angle.trading.controller;

import com.angle.trading.logging.LogLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Runtime log-level dashboard card backend.
 *
 *   GET  /api/log-level              → JSON of current levels
 *   POST /api/log-level/set?pkg=X&level=DEBUG
 *                                    → HTMX returns refreshed card; else JSON
 *
 * Takes effect immediately. Resets on JVM restart (property-file wins again).
 */
@Controller
@RequestMapping("/api/log-level")
@RequiredArgsConstructor
public class LogLevelController {

    private final LogLevelService service;

    @GetMapping
    @ResponseBody
    public Map<String, String> all() {
        return service.currentLevels();
    }

    @PostMapping("/set")
    public Object set(@RequestParam String pkg,
                      @RequestParam String level,
                      @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                      Model model) {
        String effective = service.setLevel(pkg, level);

        if (hxRequest != null) {
            model.addAttribute("logs", Map.of(
                    "levels",   service.currentLevels(),
                    "packages", LogLevelService.MANAGED_PACKAGES,
                    "labels",   LogLevelService.LABELS,
                    "choices",  LogLevelService.LEVELS));
            return "fragments/dashboard-cards :: loggingCard";
        }
        return ResponseEntity.ok(Map.of("ok", true, "pkg", pkg, "effective", effective));
    }
}
