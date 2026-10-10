package com.angle.trading.controller;

import com.angle.trading.orb.OrbInstrumentRow;
import com.angle.trading.orb.OrbService;
import com.angle.trading.orb.OrbSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * ORB dashboard — opening-range breakout signals across instruments × timeframes.
 *
 *   GET /live/orb          → HTML grid; polls the JSON endpoint every 2s
 *   GET /api/live/orb      → JSON snapshot of every (instrument, timeframe) cell
 *
 * All computation lives in OrbService; this controller is a thin HTTP skin.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class OrbController {

    private final OrbService orbService;
    private final OrbSettings orbSettings;   // injected for the config card

    @GetMapping("/live/orb")
    public String page(Model model) {
        model.addAttribute("timeframes", OrbService.TIMEFRAMES);
        model.addAttribute("orbCfg", Map.of(
                "orbCadence",       orbSettings.getOrbCadence().name(),
                "adxCadence",       orbSettings.getAdxCadence().name(),
                "adxThreshold",     orbSettings.getAdxThreshold().toPlainString(),
                "combinationMode",  orbSettings.getCombinationMode().name(),
                "cadenceChoices",   new String[]{ "TF_BOUNDARY", "EVERY_1M" },
                "thresholdChoices", new String[]{ "20", "25", "30", "35", "0" },
                "modeChoices",      new String[]{ "REQUIRE_BOTH", "ORB_ONLY", "ADX_ONLY" }));
        return "live/orb";
    }

    @GetMapping("/api/live/orb")
    @ResponseBody
    public List<OrbInstrumentRow> snapshot() {
        return orbService.snapshot();
    }
}
