package com.angle.trading.hedge;

import com.angle.trading.broker.angel.AngelLtpClient;
import com.angle.trading.broker.model.OptionType;
import com.angle.trading.journal.TradeJournalEntity;
import com.angle.trading.journal.TradeJournalRepository;
import com.angle.trading.marketdata.InstrumentMasterService;
import com.angle.trading.marketdata.model.Instrument;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/hedge")
@RequiredArgsConstructor
public class HedgeComparisonController {

    private final HedgeComparisonAnalyzer  analyzer;
    private final BiasInstrumentRepository instrumentRepo;
    private final AngelLtpClient           ltpClient;
    private final TradeJournalRepository   journalRepo;
    private final InstrumentMasterService  instrumentMaster;

    record InstrumentRow(String symbolToken, String displayName, String nseSymbol) {}

    // Display name → scrip-master underlying name for common indices
    private static final java.util.Map<String, String> INDEX_MAP = java.util.Map.ofEntries(
        java.util.Map.entry("NIFTY 50",            "NIFTY"),
        java.util.Map.entry("NIFTY50",             "NIFTY"),
        java.util.Map.entry("NIFTY",               "NIFTY"),
        java.util.Map.entry("BANKNIFTY",           "BANKNIFTY"),
        java.util.Map.entry("BANK NIFTY",          "BANKNIFTY"),
        java.util.Map.entry("NIFTY BANK",          "BANKNIFTY"),
        java.util.Map.entry("FINNIFTY",            "FINNIFTY"),
        java.util.Map.entry("NIFTY FIN SERVICE",   "FINNIFTY"),
        java.util.Map.entry("MIDCPNIFTY",          "MIDCPNIFTY"),
        java.util.Map.entry("NIFTY MID SELECT",    "MIDCPNIFTY"),
        java.util.Map.entry("SENSEX",              "SENSEX"),
        java.util.Map.entry("BANKEX",              "BANKEX")
    );

    @GetMapping
    public String page(Model model) {
        var rows = instrumentRepo.findByEnabledTrueOrderByPriorityAscIdAsc().stream()
                .map(inst -> {
                    // Derive NSE symbol from display name — strip spaces/hyphens/dots, uppercase
                    // e.g. "HDFC Bank" → "HDFCBANK", "Nifty 50" → "NIFTY50" → INDEX_MAP → "NIFTY"
                    String nse = inst.getSymbol()
                            .replaceAll("[\\s\\-\\.]+", "")
                            .toUpperCase();
                    nse = INDEX_MAP.getOrDefault(nse, nse);
                    return new InstrumentRow(inst.getSymbolToken(), inst.getSymbol(), nse);
                })
                .toList();
        model.addAttribute("instruments", rows);
        // token → nseSymbol map for inline JS lookup
        var nseMap = rows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        InstrumentRow::symbolToken, InstrumentRow::nseSymbol));
        model.addAttribute("nseSymbolMap", nseMap);
        return "hedge/compare";
    }

    // ── Analyze ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> compare(@RequestBody HedgeComparisonRequest request) {
        try {
            return ResponseEntity.ok(analyzer.compare(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Analysis failed: " + e.getMessage()));
        }
    }

    // ── Live price fetch ─────────────────────────────────────────────────────

    @GetMapping(value = "/ltp/{symbolToken}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> ltp(@PathVariable String symbolToken) {
        try {
            BiasInstrumentEntity inst = instrumentRepo.findBySymbolToken(symbolToken)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown token: " + symbolToken));
            BigDecimal price = ltpClient
                    .fetchLtp(inst.getExchange(), inst.getSymbol(), symbolToken)
                    .orElseThrow(() -> new IllegalStateException("Angel returned no LTP for " + symbolToken));
            return ResponseEntity.ok(Map.of("ltp", price, "symbol", inst.getSymbol()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "LTP fetch failed: " + e.getMessage()));
        }
    }

    // ── Option chain helpers ─────────────────────────────────────────────────

    /** Shared: derive scrip-master underlying name from equity symbolToken. */
    private String underlyingName(String symbolToken, BiasInstrumentEntity eq) {
        return instrumentMaster.findByToken(symbolToken)
                .map(i -> i.symbol()
                        .replaceAll("(?i)-EQ$|-BE$|-BL$|-IL$|-SM$|-N1$", "")
                        .trim())
                .orElse(eq.getSymbol().replaceAll("\\s+", "").toUpperCase());
    }

    /** GET /hedge/expiries?underlying=HDFCBANK  → sorted list of available expiry dates. */
    @GetMapping(value = "/expiries", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> expiries(@RequestParam String underlying) {
        try {
            if (underlying == null || underlying.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "underlying is required"));
            var dates = instrumentMaster.availableExpiries(underlying.trim().toUpperCase());
            return ResponseEntity.ok(Map.of("expiries", dates, "underlying", underlying));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /hedge/strikes?underlying=HDFCBANK&expiry=YYYY-MM-DD  → sorted distinct strikes. */
    @GetMapping(value = "/strikes", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> strikes(@RequestParam String underlying,
                                          @RequestParam String expiry) {
        try {
            LocalDate expiryDate = LocalDate.parse(expiry);
            String und           = underlying.trim().toUpperCase();
            var chain            = instrumentMaster.optionChain(und, expiryDate);
            if (chain.isEmpty()) return ResponseEntity.ok(Map.of("strikes", java.util.List.of(), "expiry", expiry));

            String exchSeg = chain.get(0).exchSeg() != null ? chain.get(0).exchSeg().toUpperCase() : "NFO";
            boolean isCds  = exchSeg.equals("CDS") || exchSeg.equals("BCD");

            var strikes = chain.stream()
                    .map(i -> {
                        if (isCds) {
                            java.math.BigDecimal raw = i.strikeValue();
                            return raw != null ? raw.movePointLeft(5) : null;
                        }
                        return parseStrikeFromSymbol(i.symbol(), und, i.exchSeg());
                    })
                    .filter(s -> s != null && s.compareTo(java.math.BigDecimal.ZERO) > 0)
                    .map(s -> s.stripTrailingZeros().toPlainString())
                    .distinct()
                    .sorted(java.util.Comparator.comparing(java.math.BigDecimal::new))
                    .toList();

            return ResponseEntity.ok(Map.of("strikes", strikes, "expiry", expiry));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Futures price fetch ──────────────────────────────────────────────────

    @GetMapping(value = "/futures-price", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> futuresPrice(@RequestParam String underlying) {
        try {
            var futures = instrumentMaster.findFutures(underlying.trim().toUpperCase());
            if (futures.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "No futures found for " + underlying));

            // Pick the nearest expiry future
            Instrument fut = futures.get(0);
            String exchSeg = fut.exchSeg() != null ? fut.exchSeg() : "NFO";
            BigDecimal ltp = ltpClient.fetchLtp(exchSeg, fut.symbol(), fut.token())
                    .orElseThrow(() -> new IllegalStateException("Angel returned no LTP for " + fut.symbol()));

            return ResponseEntity.ok(Map.of(
                    "ltp",    ltp,
                    "symbol", fut.symbol(),
                    "expiry", fut.expiry() != null ? fut.expiry() : ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Option premium fetch ─────────────────────────────────────────────────
    // GET /hedge/option-premium?underlying=HDFCBANK&strike=670&type=PE&expiry=2024-11-28

    @GetMapping(value = "/option-premium", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> optionPremium(
            @RequestParam String underlying,
            @RequestParam BigDecimal strike,
            @RequestParam String type,
            @RequestParam String expiry) {
        try {
            String und = underlying.trim().toUpperCase();

            LocalDate expiryDate = LocalDate.parse(expiry);
            OptionType optType   = OptionType.valueOf(type.toUpperCase());

            // Use parseStrikeFromSymbol so BFO paise-encoding (69000→690) and NFO both match correctly.
            // findOption() uses strikeValue() which fails for BFO options below ₹1000.
            var chain = instrumentMaster.optionChain(und, expiryDate);
            Instrument opt = chain.stream()
                    .filter(i -> optType == i.optionType())
                    .filter(i -> {
                        BigDecimal parsed = parseStrikeFromSymbol(i.symbol(), und, i.exchSeg());
                        return parsed != null && parsed.compareTo(strike) == 0;
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No option found: " + und + " " + strike + " " + type + " " + expiry));

            BigDecimal ltp = ltpClient.fetchLtp("NFO", opt.symbol(), opt.token())
                    .orElseThrow(() -> new IllegalStateException("Angel returned no LTP for " + opt.symbol()));

            return ResponseEntity.ok(Map.of(
                    "ltp",    ltp,
                    "symbol", opt.symbol(),
                    "token",  opt.token()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Save to journal ──────────────────────────────────────────────────────

    @PostMapping(value = "/journal", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Object> saveToJournal(@RequestBody HedgeJournalRequest req) {
        try {
            TradeJournalEntity entry = new TradeJournalEntity();
            entry.setSymbol(req.symbol());
            entry.setSegment("NSE");
            entry.setTradeType("BUY");
            entry.setEntryPrice(req.entryPrice());
            entry.setEntryDateTime(LocalDateTime.now());
            entry.setQuantity(req.quantity());
            entry.setTimeframe("1d");
            entry.setStrategy("Hedge: " + req.strategyName());
            entry.setIndicatorsUsed("RSI,ADX,EMA-21");
            entry.setMarketCondition(req.regime());
            entry.setReasonForEntry(req.rationale());
            entry.setNotes("PUT Strike: " + req.putStrike()
                    + " | CALL Strike: " + req.callStrike()
                    + " | PUT Premium: " + req.putPremium()
                    + " | Max Loss: " + req.maxLoss()
                    + " | Breakeven: " + req.breakeven());
            entry.setOutcome("OPEN");
            entry.setCreatedAt(Instant.now());
            entry.setUpdatedAt(Instant.now());
            journalRepo.save(entry);
            return ResponseEntity.ok(Map.of("message", "Saved to Trade Journal"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Save failed: " + e.getMessage()));
        }
    }

    /**
     * Parse the rupee strike directly from Angel's option symbol.
     * Format: {UNDERLYING}{DD}{MMM}{YY|YYYY}{STRIKE}{CE|PE}
     * e.g. "HDFCBANK29OCT26610PE"  → 610
     *      "NIFTY29OCT2624000CE"   → 24000
     */
    /**
     * Parse rupee strike from Angel option symbol.
     *
     * NFO (NSE F&O): {UNDERLYING}{DD}{MMM}{YY|YYYY}{STRIKE}{CE|PE}
     *   e.g. "HDFCBANK29DEC26560PE" → DD=29, MMM=DEC, YY=26, strike=560
     *
     * BFO (BSE F&O): {UNDERLYING}{YY}{MMM}{STRIKE}{CE|PE}
     *   e.g. "HDFCBANK26OCT640PE"  → YY=26, MMM=OCT, strike=640
     *
     * CDS/BCD handled separately (divide strikeValue by 100000).
     */
    private static java.math.BigDecimal parseStrikeFromSymbol(String symbol, String underlying, String exchSeg) {
        if (symbol == null || underlying == null) return null;
        String s = symbol.toUpperCase();
        if (s.startsWith(underlying.toUpperCase())) s = s.substring(underlying.length());

        boolean isBfo = "BFO".equalsIgnoreCase(exchSeg) || "BCD".equalsIgnoreCase(exchSeg);
        if (isBfo) {
            // BFO: YY + MMM + STRIKE + TYPE  e.g. "26OCT640PE" → remove "26OCT" → "640PE"
            s = s.replaceFirst("^\\d{2}[A-Z]{3}", "");
        } else {
            // NFO: DD + MMM + (20YY | YY) + STRIKE + TYPE  e.g. "29DEC26560PE" → remove "29DEC26" → "560PE"
            s = s.replaceFirst("^\\d{2}[A-Z]{3}(20\\d{2}|\\d{2})", "");
        }
        s = s.replaceFirst("(CE|PE)$", "");
        if (s.isEmpty()) return null;
        try {
            return new java.math.BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
