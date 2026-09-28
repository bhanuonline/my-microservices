package com.angle.trading.controller;

import com.angle.trading.ai.AiPickOutcomeService;
import com.angle.trading.ai.AiPickService;
import com.angle.trading.persistence.AiPickEntity;
import com.angle.trading.persistence.AiPickRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI stock picks — MVP.
 *
 *   GET  /portfolio                 → HTML page with picks + scorecard
 *   POST /api/ai-picks/generate     → run Claude, save new picks, return summary
 *   POST /api/ai-picks/check        → run outcome tracker manually
 *   GET  /api/ai-picks              → list all picks (JSON)
 */
@Controller
@RequiredArgsConstructor
public class PortfolioController {

    private final AiPickService pickService;
    private final AiPickOutcomeService outcomeService;
    private final AiPickRepository repo;

    // ---------- HTML page ----------

    @GetMapping("/portfolio")
    public String page(Model model) {
        List<AiPickEntity> recent = repo.findRecent(Instant.now().minusSeconds(180L * 86400));

        long openCount   = recent.stream().filter(p -> "OPEN".equals(p.getStatus())).count();
        long wonCount    = recent.stream().filter(p -> "HIT_TARGET".equals(p.getStatus())).count();
        long lostCount   = recent.stream().filter(p -> "HIT_STOP".equals(p.getStatus())).count();
        long expCount    = recent.stream().filter(p -> "EXPIRED".equals(p.getStatus())).count();

        double avgReturn = recent.stream()
                .filter(p -> p.getReturnPercent() != null)
                .mapToDouble(p -> p.getReturnPercent().doubleValue())
                .average().orElse(0.0);
        long winRate = (wonCount + lostCount) == 0 ? 0 : Math.round(100.0 * wonCount / (wonCount + lostCount));

        model.addAttribute("picks",     recent);
        model.addAttribute("openCount", openCount);
        model.addAttribute("wonCount",  wonCount);
        model.addAttribute("lostCount", lostCount);
        model.addAttribute("expCount",  expCount);
        model.addAttribute("winRate",   winRate);
        model.addAttribute("avgReturn", BigDecimal.valueOf(avgReturn).setScale(2, java.math.RoundingMode.HALF_UP));
        return "portfolio/index";
    }

    // ---------- JSON API ----------

    @PostMapping("/api/ai-picks/generate")
    @ResponseBody
    public AiPickService.GenerateResult generate() {
        return pickService.generate();
    }

    @PostMapping("/api/ai-picks/check")
    @ResponseBody
    public Map<String, Object> checkOutcomes() {
        int closed = outcomeService.checkAll();
        return Map.of("ok", true, "closed", closed);
    }

    @GetMapping("/api/ai-picks")
    @ResponseBody
    public List<AiPickEntity> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "180") int days) {
        List<AiPickEntity> all = repo.findRecent(Instant.now().minusSeconds(days * 86400L));
        if (status == null || status.isBlank()) return all;
        return all.stream().filter(p -> status.equalsIgnoreCase(p.getStatus())).toList();
    }
}
