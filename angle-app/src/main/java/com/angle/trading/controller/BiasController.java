package com.angle.trading.controller;

import com.angle.trading.bias.BiasChangeHistory;
import com.angle.trading.bias.BiasSheetService;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.InstrumentNameResolver;
import com.angle.trading.marketdata.model.Instrument;
import com.angle.trading.paper.PaperTradingSessionManager;
import com.angle.trading.paper.model.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/bias")
    public String dashboard(Model model) {
        BiasProperties.Instrument cfg = firstConfiguredOrDefault();
        return renderDashboard(model, cfg);
    }

    @GetMapping("/bias/{symbolToken}")
    public String dashboardFor(@PathVariable String symbolToken, Model model) {
        BiasProperties.Instrument cfg = findByToken(symbolToken);
        return renderDashboard(model, cfg);
    }

    /**
     * Shared render path — computes the sheet + all data feeding the dashboard.
     * Keeps the two @GetMapping methods thin.
     */
    private String renderDashboard(Model model, BiasProperties.Instrument cfg) {
        BiasSheet sheet = biasSheetService.build(cfg);
        List<BiasProperties.Instrument> instruments = instrumentsForNav();

        // Ticker strip: build a tiny snapshot per configured instrument so the
        // top of the page shows a scoreboard, not just names.
        List<Map<String, Object>> tickers = new ArrayList<>(instruments.size());
        for (BiasProperties.Instrument ins : instruments) {
            tickers.add(buildTicker(ins, cfg.getSymbolToken().equals(ins.getSymbolToken()) ? sheet : null));
        }

        model.addAttribute("sheet", sheet);
        model.addAttribute("refreshSeconds", biasProperties.getRefreshMinutes() * 60);
        model.addAttribute("instruments", instruments);
        model.addAttribute("currentToken", cfg.getSymbolToken());
        model.addAttribute("tickers", tickers);
        model.addAttribute("recentChanges", biasChangeHistory.recent());
        model.addAttribute("activeSessions", sessionManager.list());
        return "bias/dashboard";
    }

    /** Compact snapshot for a ticker card. Fetches the full sheet only if we don't have it. */
    private Map<String, Object> buildTicker(BiasProperties.Instrument ins, BiasSheet reuse) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("symbol",      ins.getSymbol());
        t.put("symbolToken", ins.getSymbolToken());
        t.put("exchange",    ins.getExchange().name());
        BiasSheet s = reuse != null ? reuse : safeBuild(ins);
        if (s == null || s.marketContext() == null) {
            t.put("price", null);
            t.put("changePercent", null);
            t.put("recommendation", "—");
            t.put("score", null);
            return t;
        }
        BigDecimal price = s.marketContext().currentPrice();
        BigDecimal prev  = s.marketContext().previousClose();
        BigDecimal pct = null;
        if (price != null && prev != null && prev.signum() > 0) {
            pct = price.subtract(prev)
                    .divide(prev, MathContext.DECIMAL64)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        t.put("price", price);
        t.put("changePercent", pct);
        t.put("recommendation", s.consolidated().recommendation());
        t.put("score", s.consolidated().totalScore());
        return t;
    }

    private BiasSheet safeBuild(BiasProperties.Instrument ins) {
        try {
            return biasSheetService.build(ins);
        } catch (Exception e) {
            return null;
        }
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
