package com.angle.trading.screener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/screener")
@RequiredArgsConstructor
public class FoScreenerController {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("Asia/Kolkata"));

    private final FoScreenerService screenerService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "false") boolean refresh, Model model) {
        List<ScreenerRow> rows = screenerService.scan(refresh);

        long ready  = rows.stream().filter(r -> r.getError() == null && r.getScore() >= 4).count();
        long decent = rows.stream().filter(r -> r.getError() == null && r.getScore() == 3).count();
        long weak   = rows.stream().filter(r -> r.getError() == null && r.getScore() <= 2).count();
        long errors = rows.stream().filter(r -> r.getError() != null).count();

        model.addAttribute("rows",        rows);
        model.addAttribute("ready",       ready);
        model.addAttribute("decent",      decent);
        model.addAttribute("weak",        weak);
        model.addAttribute("errors",      errors);
        model.addAttribute("lastScanned", FMT.format(screenerService.lastScannedAt()));
        model.addAttribute("stale",       screenerService.isCacheStale());

        return "screener/index";
    }
}
