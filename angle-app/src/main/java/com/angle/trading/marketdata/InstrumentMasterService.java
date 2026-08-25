package com.angle.trading.marketdata;

import com.angle.trading.broker.model.OptionType;
import com.angle.trading.marketdata.model.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Downloads and indexes Angel's daily scrip master file.
 *
 * Speed tricks:
 *   1. On-disk cache at target/angel-scrip-master.json — subsequent starts
 *      skip the ~30 MB download if the cache is younger than {@link #CACHE_TTL}.
 *   2. Load happens on a background thread so the app finishes booting
 *      immediately; searches return empty until the load finishes.
 *   3. Dedicated RestClient with a 2-minute read timeout + gzip request
 *      header, so the download completes even on slow links.
 *
 * Call {@link #refresh()} (or POST /api/instruments/refresh) to force-fetch
 * the latest file from Angel, ignoring the cache.
 */
@Slf4j
@Service
public class InstrumentMasterService {

    private static final String MASTER_URL =
            "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";

    private static final Path   CACHE_FILE = Path.of("target", "angel-scrip-master.json");
    private static final Duration CACHE_TTL = Duration.ofHours(20);

    private final RestClient    restClient;
    private final ObjectMapper  objectMapper;

    private volatile Map<String, Instrument> bySymbol = Collections.emptyMap();
    private volatile Map<String, Instrument> byToken  = Collections.emptyMap();

    public InstrumentMasterService(@Qualifier("masterFileRestClient") RestClient restClient,
                                   ObjectMapper objectMapper) {
        this.restClient   = restClient;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialLoad() {
        Thread t = new Thread(() -> {
            try {
                loadCachedOrFetch();
            } catch (Exception e) {
                log.warn("Initial scrip master load failed — searches will return empty until refreshed. Cause: {}",
                        e.getMessage());
            }
        }, "scrip-master-loader");
        t.setDaemon(true);
        t.start();
    }

    /** Force download from Angel, ignore any cached file. */
    public synchronized void refresh() {
        long start = System.currentTimeMillis();
        log.info("Downloading Angel scrip master from {}", MASTER_URL);

        Instrument[] all = restClient.get()
                .uri(MASTER_URL)
                .retrieve()
                .body(Instrument[].class);

        rebuildIndex(all, "network", start);
        writeCache(all);
    }

    private synchronized void loadCachedOrFetch() throws IOException {
        long start = System.currentTimeMillis();

        if (isCacheFresh()) {
            log.info("Loading scrip master from local cache {}", CACHE_FILE);
            Instrument[] all = objectMapper.readValue(CACHE_FILE.toFile(), Instrument[].class);
            rebuildIndex(all, "cache", start);
            return;
        }

        log.info("Cache stale or missing, downloading Angel scrip master from {}", MASTER_URL);
        Instrument[] all = restClient.get()
                .uri(MASTER_URL)
                .retrieve()
                .body(Instrument[].class);
        rebuildIndex(all, "network", start);
        writeCache(all);
    }

    private void rebuildIndex(Instrument[] all, String source, long start) {
        if (all == null || all.length == 0) {
            throw new IllegalStateException("Scrip master had no instruments (source=" + source + ")");
        }
        Map<String, Instrument> bySym = new HashMap<>(all.length);
        Map<String, Instrument> byTok = new HashMap<>(all.length);
        for (Instrument i : all) {
            if (i.symbol() != null) bySym.put(i.symbol(), i);
            if (i.token()  != null) byTok.put(i.token(), i);
        }
        this.bySymbol = bySym;
        this.byToken  = byTok;
        log.info("Scrip master loaded from {}: {} instruments in {} ms",
                source, all.length, System.currentTimeMillis() - start);
    }

    private boolean isCacheFresh() {
        try {
            if (!Files.exists(CACHE_FILE)) return false;
            Instant modified = Files.getLastModifiedTime(CACHE_FILE).toInstant();
            return Duration.between(modified, Instant.now()).compareTo(CACHE_TTL) < 0;
        } catch (IOException e) {
            log.debug("Could not stat cache file: {}", e.getMessage());
            return false;
        }
    }

    private void writeCache(Instrument[] all) {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            Path tmp = CACHE_FILE.resolveSibling(CACHE_FILE.getFileName() + ".tmp");
            objectMapper.writeValue(tmp.toFile(), all);
            Files.move(tmp, CACHE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.debug("Cached scrip master to {}", CACHE_FILE);
        } catch (IOException e) {
            log.warn("Failed to write scrip master cache: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Lookup API
    // ------------------------------------------------------------

    public int size() {
        return bySymbol.size();
    }

    public Optional<Instrument> findBySymbol(String symbol) {
        return Optional.ofNullable(bySymbol.get(symbol));
    }

    public Optional<Instrument> findByToken(String token) {
        return Optional.ofNullable(byToken.get(token));
    }

    public Optional<Instrument> findOption(String underlying, LocalDate expiry,
                                           BigDecimal strike, OptionType type) {
        return bySymbol.values().stream()
                .filter(Instrument::isOption)
                .filter(i -> underlying.equalsIgnoreCase(i.name()))
                .filter(i -> Objects.equals(expiry, i.expiryDate()))
                .filter(i -> type == i.optionType())
                .filter(i -> i.strikeValue() != null && i.strikeValue().compareTo(strike) == 0)
                .findFirst();
    }

    public List<Instrument> findFutures(String underlying) {
        return bySymbol.values().stream()
                .filter(Instrument::isFuture)
                .filter(i -> underlying.equalsIgnoreCase(i.name()))
                .sorted(Comparator.comparing(Instrument::expiryDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<LocalDate> availableExpiries(String underlying) {
        return bySymbol.values().stream()
                .filter(Instrument::isOption)
                .filter(i -> underlying.equalsIgnoreCase(i.name()))
                .map(Instrument::expiryDate)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public List<Instrument> optionChain(String underlying, LocalDate expiry) {
        return bySymbol.values().stream()
                .filter(Instrument::isOption)
                .filter(i -> underlying.equalsIgnoreCase(i.name()))
                .filter(i -> Objects.equals(expiry, i.expiryDate()))
                .sorted(Comparator
                        .comparing((Instrument i) -> i.strikeValue(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Instrument::optionType))
                .toList();
    }
}
