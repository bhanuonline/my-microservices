package com.angle.trading.controller;

import com.angle.trading.bias.BiasSheetService;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.config.BiasProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

    @GetMapping("/bias")
    public String dashboard(Model model) {
        BiasProperties.Instrument cfg = firstConfiguredOrDefault();
        BiasSheet sheet = biasSheetService.build(cfg);
        model.addAttribute("sheet", sheet);
        model.addAttribute("refreshSeconds", biasProperties.getRefreshMinutes() * 60);
        return "bias/dashboard";
    }

    @GetMapping("/bias/{symbolToken}")
    public String dashboardFor(@PathVariable String symbolToken, Model model) {
        BiasProperties.Instrument cfg = findByToken(symbolToken);
        BiasSheet sheet = biasSheetService.build(cfg);
        model.addAttribute("sheet", sheet);
        model.addAttribute("refreshSeconds", biasProperties.getRefreshMinutes() * 60);
        return "bias/dashboard";
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

    private BiasProperties.Instrument findByToken(String symbolToken) {
        return biasProperties.getInstruments().stream()
                .filter(i -> symbolToken.equals(i.getSymbolToken()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No instrument configured with symbolToken=" + symbolToken));
    }

    /** Nifty 50 fallback when nothing is configured. */
    private static BiasProperties.Instrument defaultInstrument() {
        BiasProperties.Instrument def = new BiasProperties.Instrument();
        def.setSymbol("Nifty 50");
        def.setSymbolToken("99926000");
        return def;
    }
}
