package com.angle.trading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the live analysis UI pages.
 * All data is loaded client-side via fetch() calls to /api/stocks/analysis.
 */
@Controller
public class LiveAnalysisController {

    /** Single-symbol deep analysis page. */
    @GetMapping("/live/analysis")
    public String analysisPage() {
        return "live/analysis";
    }

    /** Multi-symbol watchlist page. */
    @GetMapping("/live/watchlist")
    public String watchlistPage() {
        return "live/watchlist";
    }
}
