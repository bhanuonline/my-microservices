package com.angle.trading.controller;

import com.angle.trading.ai.AiSignalService;
import com.angle.trading.ai.model.AiSignal;
import com.angle.trading.bias.BiasSheetService;
import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.config.BiasProperties;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.InstrumentNameResolver;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * On-demand AI opinion endpoint.
 *
 *   GET /api/ai/signal?token=99926000
 *      → builds current BiasSheet for the token, runs it through the LLM,
 *        returns AiSignal JSON. Cached 5 min per (token, asOf) pair to
 *        avoid re-billing on repeat clicks.
 *
 * Wired to a button on /bias page. Also directly callable from curl/Postman.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiSignalController {

    private final AiSignalService aiSignalService;
    private final BiasSheetService biasSheetService;
    private final BiasProperties biasProperties;
    private final InstrumentMasterService instrumentMasterService;
    private final InstrumentNameResolver instrumentNameResolver;

    @GetMapping("/signal")
    public Map<String, Object> signal(@RequestParam String token) {
        BiasProperties.Instrument cfg = findInstrument(token);
        BiasSheet sheet;
        try {
            sheet = biasSheetService.build(cfg);
        } catch (Exception e) {
            log.warn("AI signal — failed to build bias sheet for {}: {}", token, e.getMessage());
            return Map.of(
                    "error", "Failed to build bias sheet: " + e.getMessage(),
                    "token", token
            );
        }
        AiSignal signal = aiSignalService.analyse(sheet);
        return Map.of(
                "symbol",       sheet.symbol(),
                "symbolToken",  sheet.symbolToken(),
                "action",       signal.action(),
                "confidence",   signal.confidence(),
                "rationale",    signal.rationale(),
                "keyPoints",    signal.keyPoints(),
                "model",        signal.model(),
                "cached",       signal.cached(),
                "generatedAt",  signal.generatedAt().toString()
        );
    }

    /** Same instrument-resolution pattern as BiasController. */
    private BiasProperties.Instrument findInstrument(String symbolToken) {
        return biasProperties.getInstruments().stream()
                .filter(i -> symbolToken.equals(i.getSymbolToken()))
                .findFirst()
                .orElseGet(() -> {
                    BiasProperties.Instrument adhoc = new BiasProperties.Instrument();
                    adhoc.setSymbolToken(symbolToken);
                    adhoc.setSymbol(instrumentNameResolver.resolve(symbolToken));
                    instrumentMasterService.findByToken(symbolToken)
                            .map(Instrument::exchange)
                            .ifPresent(adhoc::setExchange);
                    return adhoc;
                });
    }
}
