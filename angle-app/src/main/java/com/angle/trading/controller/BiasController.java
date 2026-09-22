package com.angle.trading.controller;

import com.angle.trading.bias.BiasChangeHistory;
import com.angle.trading.bias.BiasSheetService;
import com.angle.trading.bias.TickerFetcher;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.InstrumentNameResolver;
import com.angle.trading.marketdata.model.Instrument;
import com.angle.trading.paper.PaperTradingSessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard endpoints.
 *
 *   GET /bias               → HTML page (default instrument, or ?symbolToken= override)
 *   GET /bias/{symbolToken} → HTML page for a specific instrument
 *   GET /api/bias/sheet     → JSON of default instrument's sheet
 *   GET /api/bias/sheet/{symbolToken} → JSON for one instrument
 *   GET /api/bias/all       → JSON list of every configured instrument's sheet
 */
@Controller
@RequiredArgsConstructor
public class BiasController {

    private final BiasSheetService biasSheetService;
    private final BiasProperties biasProperties;
    private final InstrumentNameResolver instrumentNameResolver;
    private final InstrumentMasterService instrumentMasterService;
    private final BiasChangeHistory biasChangeHistory;
    private final PaperTradingSessionManager sessionManager;
    private final TickerFetcher tickerFetcher;

    @GetMapping("/bias")
    public String dashboard(
            Model model,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf
    ) {
        BiasProperties.Instrument cfg = firstConfiguredOrDefault();
        return renderDashboard(model, cfg, asOf);
    }

    @GetMapping("/bias/{symbolToken}")
    public String dashboardFor(
            @PathVariable String symbolToken,
            Model model,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime asOf
    ) {
        BiasProperties.Instrument cfg = findByToken(symbolToken);
        return renderDashboard(model, cfg, asOf);
    }

    /**
     * Shared render path.
     * If asOfLocal is set → historical mode: all data as-of that past instant,
     * auto-refresh disabled, banner shown.
     */
    private String renderDashboard(Model model, BiasProperties.Instrument cfg, LocalDateTime asOfLocal) {
        Instant asOfInstant = asOfLocal == null ? null : asOfLocal.atZone(ZoneId.systemDefault()).toInstant();
        boolean historical = asOfInstant != null;

        BiasSheet sheet = biasSheetService.buildAsOf(cfg, asOfInstant);
        List<BiasProperties.Instrument> instruments = instrumentsForNav();

        // Ticker strip snapshots — parallel (or sequential) based on bias.ticker.parallel-enabled.
        List<TickerFetcher.Ticker> tickers =
                tickerFetcher.fetchAll(instruments, cfg.getSymbolToken(), sheet, asOfInstant);

        model.addAttribute("sheet", sheet);
        // Historical mode disables auto-refresh (data doesn't change).
        model.addAttribute("refreshSeconds", historical ? 0 : biasProperties.getRefreshMinutes() * 60);
        model.addAttribute("instruments", instruments);
        model.addAttribute("currentToken", cfg.getSymbolToken());
        model.addAttribute("tickers", tickers);
        model.addAttribute("recentChanges", biasChangeHistory.recent());
        model.addAttribute("activeSessions", sessionManager.list());
        model.addAttribute("historical", historical);
        model.addAttribute("asOfDisplay", historical ? asOfLocal.toString() : null);
        return "bias/dashboard";
    }

    /**
     * List of instruments to show as buttons at the top of the dashboard.
     * Uses configured instruments if any; otherwise falls back to the
     * built-in Nifty 50 default.
     */
    private List<BiasProperties.Instrument> instrumentsForNav() {
        List<BiasProperties.Instrument> list = biasProperties.getInstruments();
        return list.isEmpty() ? List.of(defaultInstrument()) : list;
    }

    @GetMapping("/api/bias/sheet")
    @ResponseBody
    public BiasSheet sheetJson() {
        return biasSheetService.build(firstConfiguredOrDefault());
    }

    @GetMapping("/api/bias/sheet/{symbolToken}")
    @ResponseBody
    public BiasSheet sheetJsonFor(@PathVariable String symbolToken) {
        return biasSheetService.build(findByToken(symbolToken));
    }

    @GetMapping("/api/bias/all")
    @ResponseBody
    public List<BiasSheet> allInstruments() {
        List<BiasProperties.Instrument> cfgs = biasProperties.getInstruments();
        if (cfgs.isEmpty()) cfgs = List.of(defaultInstrument());
        List<BiasSheet> out = new ArrayList<>(cfgs.size());
        for (BiasProperties.Instrument cfg : cfgs) {
            out.add(biasSheetService.build(cfg));
        }
        return out;
    }

    // ---------- helpers ----------

    private BiasProperties.Instrument firstConfiguredOrDefault() {
        List<BiasProperties.Instrument> list = biasProperties.getInstruments();
        return list.isEmpty() ? defaultInstrument() : list.get(0);
    }

    /**
     * Look up a configured instrument, or build one on the fly if the token
     * isn't in config. Uses the scrip master to fill in the right exchange
     * (so MCX / NFO / etc. work correctly) and InstrumentNameResolver for
     * the human name.
     */
    private BiasProperties.Instrument findByToken(String symbolToken) {
        return biasProperties.getInstruments().stream()
                .filter(i -> symbolToken.equals(i.getSymbolToken()))
                .findFirst()
                .orElseGet(() -> {
                    BiasProperties.Instrument adhoc = new BiasProperties.Instrument();
                    adhoc.setSymbolToken(symbolToken);
                    adhoc.setSymbol(instrumentNameResolver.resolve(symbolToken));
                    // Pull correct exchange (NSE / NFO / MCX / CDS) from the scrip master
                    instrumentMasterService.findByToken(symbolToken)
                            .map(Instrument::exchange)
                            .ifPresent(adhoc::setExchange);
                    return adhoc;
                });
    }

    /** Nifty 50 fallback when nothing is configured. */
    private static BiasProperties.Instrument defaultInstrument() {
        BiasProperties.Instrument def = new BiasProperties.Instrument();
        def.setSymbol("Nifty 50");
        def.setSymbolToken("99926000");
        return def;
    }
}
