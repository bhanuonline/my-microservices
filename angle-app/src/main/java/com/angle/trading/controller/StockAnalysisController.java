package com.angle.trading.controller;

import com.angle.trading.analysis.AnalysisParams;
import com.angle.trading.analysis.StockAnalysisService;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.dto.AnalysisStatus;
import com.angle.trading.dto.BulkAnalysisRequestDto;
import com.angle.trading.dto.BulkAnalysisResponseDto;
import com.angle.trading.dto.StockAnalysisDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consolidated stock analysis endpoints.
 *
 * <pre>
 *   GET  /api/stocks/analysis       — single symbol, all modes
 *   POST /api/stocks/analysis/bulk  — multiple symbols in parallel
 * </pre>
 *
 * All business logic is in {@link StockAnalysisService} — this controller
 * is a thin HTTP layer only: parse params, delegate, return response.
 */
@Slf4j
@RestController
@RequestMapping("/api/stocks/analysis")
@RequiredArgsConstructor
public class StockAnalysisController {

    private final StockAnalysisService analysisService;

    /** Named thread pool for bulk parallel execution. */
    private static final Executor BULK_EXECUTOR = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger();
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "bulk-analysis-" + count.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            });

    // ── Single symbol ────────────────────────────────────────────────

    /**
     * Full stock analysis for a single symbol.
     *
     * <p>Mode is auto-detected from params:
     * <ul>
     *   <li>live=true               → LIVE (progressive, nextCandleAt)</li>
     *   <li>live=false + asOf given → HISTORICAL (no live price)</li>
     *   <li>live=false + no asOf   → CONSOLIDATED (today + live price)</li>
     * </ul>
     *
     * @param symbol      Angel scrip symbol e.g. HDFCBANK-EQ
     * @param live        enable live progressive mode
     * @param interval    highest TF in MTF stack
     * @param baseInterval smallest TF (default from config)
     * @param days        warmup history in calendar days
     * @param asOf        historical date (ignored when live=true)
     * @param breakdown   include candle-by-candle dayBreakdown
     * @param marketOpen  override session open "HH:mm"
     * @param marketClose override session close "HH:mm"
     */
    @GetMapping
    public ResponseEntity<?> analyze(
            @RequestParam                                              String    symbol,
            @RequestParam(defaultValue = "false")                     boolean   live,
            @RequestParam(defaultValue = "ONE_DAY")                   Interval  interval,
            @RequestParam(required = false)                            Interval  baseInterval,
            @RequestParam(defaultValue = "60")                        int       days,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)         LocalDate asOf,
            @RequestParam(defaultValue = "false")                     boolean   breakdown,
            @RequestParam(required = false)                            String    marketOpen,
            @RequestParam(required = false)                            String    marketClose
    ) {
        log.info("GET /api/stocks/analysis symbol={} live={} interval={} days={} asOf={}",
                symbol, live, interval, days, asOf);

        AnalysisParams params = new AnalysisParams(
                symbol, live, interval, baseInterval, days,
                asOf, breakdown, marketOpen, marketClose);

        StockAnalysisDto result = analysisService.analyze(params);

        // Return 404 for symbol-not-found, 200 for everything else
        if (result.status() == AnalysisStatus.ERROR
                && result.error() != null
                && result.error().contains("not found in scrip master")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    // ── Bulk (multiple symbols) ──────────────────────────────────────

    /**
     * Analyse multiple symbols in parallel.
     * Each symbol can have its own interval, baseInterval, days, and asOf.
     *
     * <p>Symbols are processed concurrently using a named thread pool.
     * If one symbol fails, the others still return — check each result's
     * {@code status} field (OK / STALE / ERROR).
     *
     * @param request bulk request body
     */
    @PostMapping("/bulk")
    public ResponseEntity<BulkAnalysisResponseDto> bulk(
            @RequestBody BulkAnalysisRequestDto request) {

        if (request.symbols() == null || request.symbols().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate: symbol count within limit
        try {
            analysisService.validateBulkSize(request.symbols().size());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new BulkAnalysisResponseDto(
                            request.symbols().size(), 0,
                            request.symbols().size(), 0,
                            List.of(StockAnalysisDto.error("*", e.getMessage()))));
        }

        // Validate: breakdown=true capped at 3 symbols (response would be too large otherwise)
        if (request.breakdown() && request.symbols().size() > 3) {
            String msg = "breakdown=true is limited to 3 symbols in bulk. You sent: "
                    + request.symbols().size() + ". Reduce symbols or use breakdown=false.";
            return ResponseEntity.badRequest()
                    .body(new BulkAnalysisResponseDto(
                            request.symbols().size(), 0,
                            request.symbols().size(), 0,
                            List.of(StockAnalysisDto.error("*", msg))));
        }

        // Validate: no null/blank symbols
        boolean hasBlankSymbol = request.symbols().stream()
                .anyMatch(s -> s.symbol() == null || s.symbol().isBlank());
        if (hasBlankSymbol) {
            return ResponseEntity.badRequest()
                    .body(new BulkAnalysisResponseDto(
                            request.symbols().size(), 0,
                            request.symbols().size(), 0,
                            List.of(StockAnalysisDto.error("*", "All symbols must have a non-blank 'symbol' field."))));
        }

        log.info("POST /api/stocks/analysis/bulk symbols={} live={} breakdown={}",
                request.symbols().size(), request.live(), request.breakdown());

        long start = System.currentTimeMillis();

        // Process all symbols in parallel
        List<CompletableFuture<StockAnalysisDto>> futures = request.symbols().stream()
                .map(req -> CompletableFuture.supplyAsync(
                        () -> analysisService.analyze(new AnalysisParams(
                                req.symbol(),
                                request.live(),
                                req.interval()     != null ? req.interval()     : Interval.ONE_DAY,
                                req.baseInterval(),
                                req.days()         != null ? req.days()         : 60,
                                req.asOf(),
                                request.breakdown(),
                                req.marketOpen(),
                                req.marketClose())),
                        BULK_EXECUTOR))
                .toList();

        // Wait for all to complete
        List<StockAnalysisDto> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long processingMs = System.currentTimeMillis() - start;
        log.info("Bulk analysis complete: {} symbols in {}ms", results.size(), processingMs);

        return ResponseEntity.ok(BulkAnalysisResponseDto.of(results, processingMs));
    }
}
