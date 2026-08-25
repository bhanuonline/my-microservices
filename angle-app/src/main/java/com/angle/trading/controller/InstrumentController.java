package com.angle.trading.controller;

import com.angle.trading.broker.model.OptionType;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Lookup endpoints backed by {@link InstrumentMasterService}.
 *
 *   GET  /api/instruments/lookup?symbol=NIFTY28AUG2624700CE
 *   GET  /api/instruments/lookup-by-token?token=48601
 *   GET  /api/instruments/option?underlying=NIFTY&expiry=2026-08-28&strike=24700&type=CE
 *   GET  /api/instruments/expiries?underlying=NIFTY
 *   GET  /api/instruments/option-chain?underlying=NIFTY&expiry=2026-08-28
 *   GET  /api/instruments/futures?underlying=NIFTY
 *   POST /api/instruments/refresh
 *   GET  /api/instruments/status
 */
@RestController
@RequestMapping("/api/instruments")
@RequiredArgsConstructor
public class InstrumentController {

    private final InstrumentMasterService instruments;

    @GetMapping("/lookup")
    public ResponseEntity<Instrument> lookupBySymbol(@RequestParam String symbol) {
        return instruments.findBySymbol(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/lookup-by-token")
    public ResponseEntity<Instrument> lookupByToken(@RequestParam String token) {
        return instruments.findByToken(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/option")
    public ResponseEntity<Instrument> findOption(
            @RequestParam String underlying,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiry,
            @RequestParam BigDecimal strike,
            @RequestParam OptionType type
    ) {
        return instruments.findOption(underlying, expiry, strike, type)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/expiries")
    public List<LocalDate> expiries(@RequestParam String underlying) {
        return instruments.availableExpiries(underlying);
    }

    @GetMapping("/option-chain")
    public List<Instrument> optionChain(
            @RequestParam String underlying,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiry
    ) {
        return instruments.optionChain(underlying, expiry);
    }

    @GetMapping("/futures")
    public List<Instrument> futures(@RequestParam String underlying) {
        return instruments.findFutures(underlying);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        instruments.refresh();
        return Map.of("status", "ok", "count", instruments.size());
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("count", instruments.size());
    }
}
