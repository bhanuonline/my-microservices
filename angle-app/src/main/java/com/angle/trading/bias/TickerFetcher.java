package com.angle.trading.bias;

import com.angle.trading.bias.model.BiasSheet;
import com.angle.trading.config.BiasProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fetches ticker snapshots (price + change + recommendation) for every
 * configured instrument. Two modes, driven by config:
 *
 *   parallel=true  → all instruments fetched concurrently via a thread pool
 *   parallel=false → old sequential behaviour (kept for debugging + rollback)
 *
 * NO caching — every call fetches fresh from Angel. Data is always current.
 *
 * The already-built sheet for the "current" instrument is passed in and
 * reused so we don't fetch it twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TickerFetcher {

    private final BiasSheetService biasSheetService;
    private final BiasProperties biasProperties;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        int size = Math.max(1, biasProperties.getTicker().getThreadPoolSize());
        this.executor = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, "ticker-fetch");
            t.setDaemon(true);
            return t;
        });
        log.info("TickerFetcher initialised: parallel={}, poolSize={}, timeoutSec={}",
                biasProperties.getTicker().isParallelEnabled(),
                size,
                biasProperties.getTicker().getTimeoutSeconds());
    }

    /** Ticker snapshot shown at the top of the dashboard. */
    public record Ticker(
            String symbol, String symbolToken, String exchange,
            BigDecimal price, BigDecimal changePercent,
            String recommendation, int score) {}

    /**
     * Fetch tickers for all instruments.
     *   currentToken + currentSheet — the sheet we already built for the
     *   instrument being viewed. Reused to avoid a duplicate Angel call.
     */
    public List<Ticker> fetchAll(List<BiasProperties.Instrument> instruments,
                                 String currentToken,
                                 BiasSheet currentSheet,
                                 Instant asOf) {
        boolean parallel = biasProperties.getTicker().isParallelEnabled();
        return parallel
                ? fetchParallel(instruments, currentToken, currentSheet, asOf)
                : fetchSequential(instruments, currentToken, currentSheet, asOf);
    }

    // ---------- parallel mode ----------

    private List<Ticker> fetchParallel(List<BiasProperties.Instrument> instruments,
                                       String currentToken,
                                       BiasSheet currentSheet,
                                       Instant asOf) {
        int timeoutSec = biasProperties.getTicker().getTimeoutSeconds();
        long start = System.currentTimeMillis();

        List<CompletableFuture<Ticker>> futures = instruments.stream()
                .map(ins -> CompletableFuture.supplyAsync(
                        () -> fetchOne(ins,
                                ins.getSymbolToken().equals(currentToken) ? currentSheet : null,
                                asOf),
                        executor))
                .toList();

        List<Ticker> out = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            BiasProperties.Instrument ins = instruments.get(i);
            try {
                out.add(futures.get(i).get(timeoutSec, TimeUnit.SECONDS));
            } catch (TimeoutException te) {
                log.warn("Ticker fetch TIMEOUT for {} after {}s", ins.getSymbol(), timeoutSec);
                out.add(emptyTicker(ins));
                futures.get(i).cancel(true);
            } catch (Exception e) {
                log.warn("Ticker fetch failed for {}: {}", ins.getSymbol(), e.getMessage());
                out.add(emptyTicker(ins));
            }
        }
        log.debug("Parallel ticker fetch complete: {} instruments in {} ms",
                instruments.size(), System.currentTimeMillis() - start);
        return out;
    }

    // ---------- sequential mode (rollback / debug) ----------

    private List<Ticker> fetchSequential(List<BiasProperties.Instrument> instruments,
                                         String currentToken,
                                         BiasSheet currentSheet,
                                         Instant asOf) {
        long start = System.currentTimeMillis();
        List<Ticker> out = new ArrayList<>(instruments.size());
        for (BiasProperties.Instrument ins : instruments) {
            BiasSheet reuse = ins.getSymbolToken().equals(currentToken) ? currentSheet : null;
            out.add(fetchOne(ins, reuse, asOf));
        }
        log.debug("Sequential ticker fetch complete: {} instruments in {} ms",
                instruments.size(), System.currentTimeMillis() - start);
        return out;
    }

    // ---------- single instrument ----------

    private Ticker fetchOne(BiasProperties.Instrument ins, BiasSheet reuse, Instant asOf) {
        try {
            BiasSheet s = reuse != null ? reuse : biasSheetService.buildAsOf(ins, asOf);
            if (s == null || s.marketContext() == null) return emptyTicker(ins);

            BigDecimal price = s.marketContext().currentPrice();
            BigDecimal prev  = s.marketContext().previousClose();
            BigDecimal pct = (price != null && prev != null && prev.signum() > 0)
                    ? price.subtract(prev).divide(prev, MathContext.DECIMAL64)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : null;
            return new Ticker(
                    ins.getSymbol(), ins.getSymbolToken(), ins.getExchange().name(),
                    price, pct,
                    s.consolidated() != null ? s.consolidated().recommendation() : "—",
                    s.consolidated() != null ? s.consolidated().totalScore() : 0);
        } catch (Exception e) {
            log.debug("Ticker fetch failed for {}: {}", ins.getSymbol(), e.getMessage());
            return emptyTicker(ins);
        }
    }

    private static Ticker emptyTicker(BiasProperties.Instrument ins) {
        return new Ticker(ins.getSymbol(), ins.getSymbolToken(), ins.getExchange().name(),
                null, null, "—", 0);
    }

    @PreDestroy
    public void shutdown() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
