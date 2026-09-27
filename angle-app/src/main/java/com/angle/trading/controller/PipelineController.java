package com.angle.trading.controller;

import com.angle.trading.analysis.MtfConfirmationService;
import com.angle.trading.analysis.PipelineStats;
import com.angle.trading.analysis.RegimeService;
import com.angle.trading.config.MtfProperties;
import com.angle.trading.config.NewsProperties;
import com.angle.trading.config.RegimeProperties;
import com.angle.trading.config.SignalsProperties;
import com.angle.trading.config.TrailingProperties;
import com.angle.trading.news.NewsCalendarService;
import com.angle.trading.persistence.SignalEntity;
import com.angle.trading.service.SignalService;
import com.angle.trading.service.TrailingStopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Unified control panel for the signal pipeline.
 *
 * Aggregates state from RegimeService / MtfConfirmationService / TrailingStopService /
 * NewsCalendarService into one Thymeleaf model, plus current PipelineStats counts.
 *
 * Toggles on the page hit the individual /api/{gate}/enable endpoints — this
 * controller is READ-ONLY. Keeps concerns cleanly separated.
 */
@Controller
@RequiredArgsConstructor
public class PipelineController {

    private final RegimeService regimeService;
    private final MtfConfirmationService mtfService;
    private final TrailingStopService trailingStopService;
    private final NewsCalendarService newsCalendarService;
    private final SignalService signalService;
    private final PipelineStats pipelineStats;

    private final RegimeProperties   regimeProps;
    private final MtfProperties      mtfProps;
    private final TrailingProperties trailingProps;
    private final NewsProperties     newsProps;
    private final SignalsProperties  signalsProps;

    @GetMapping("/admin/pipeline")
    public String page(Model model) {
        // Snapshots
        model.addAttribute("regime",   regimeService.snapshot());
        model.addAttribute("mtf",      mtfService.snapshot());
        model.addAttribute("trailing", trailingStopService.snapshot());
        model.addAttribute("news",     newsCalendarService.snapshot());
        model.addAttribute("stats",    pipelineStats.snapshot());

        // Config for toggles + display of the AI-confirmer flag
        model.addAttribute("newsProps",     newsProps);
        model.addAttribute("regimeProps",   regimeProps);
        model.addAttribute("mtfProps",      mtfProps);
        model.addAttribute("trailingProps", trailingProps);
        model.addAttribute("signalsProps",  signalsProps);

        // Active OPEN signals + trailing count
        List<SignalEntity> open = signalService.findOpen();
        long activeTrails = open.stream().filter(s -> s.getTrailUpdates() > 0).count();
        model.addAttribute("openCount",    open.size());
        model.addAttribute("activeTrails", activeTrails);

        // Active news events (visible warning strip)
        model.addAttribute("activeNews", newsCalendarService.active());

        return "admin/pipeline";
    }
}
