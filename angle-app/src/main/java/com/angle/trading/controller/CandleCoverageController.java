package com.angle.trading.controller;

import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.persistence.BiasInstrumentRepository;
import com.angle.trading.persistence.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Candle Coverage page — shows per-instrument, per-interval data coverage.
 *
 *   GET /coverage  → HTML page
 */
@Controller
@RequiredArgsConstructor
public class CandleCoverageController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final List<String> INTERVAL_ORDER =
            List.of("ONE_DAY", "ONE_HOUR", "FIFTEEN_MINUTE", "FIVE_MINUTE", "ONE_MINUTE");

    private final BiasInstrumentRepository instrumentRepo;
    private final CandleRepository         candleRepo;

    public record CoverageRow(
            String  symbol,
            String  symbolToken,
            String  exchange,
            String  intervalType,
            boolean enabled,
            LocalDate firstDate,
            LocalDate latestDate,
            long    candleCount,
            long    daysBehind,
            String  status          // OK / STALE / MISSING
    ) {}

    @GetMapping("/coverage")
    public String page(Model model) {
        // All instruments (enabled + disabled)
        List<BiasInstrumentEntity> instruments = instrumentRepo.findAllByOrderByPriorityAscIdAsc();

        // Build lookup: symbolToken → instrument
        Map<String, BiasInstrumentEntity> byToken = new LinkedHashMap<>();
        for (BiasInstrumentEntity i : instruments) byToken.put(i.getSymbolToken(), i);

        // Coverage summary from DB
        List<Object[]> summary = candleRepo.findCoverageSummary();

        // Build map: token → interval → coverage row
        Map<String, Map<String, CoverageRow>> coverageMap = new LinkedHashMap<>();

        LocalDate today = LocalDate.now(IST);

        for (Object[] row : summary) {
            String token        = (String) row[0];
            String interval     = (String) row[1];
            Instant firstTs     = toInstant(row[2]);
            Instant latestTs    = toInstant(row[3]);
            long    count       = toLong(row[4]);

            BiasInstrumentEntity instr = byToken.get(token);
            String symbol   = instr != null ? instr.getSymbol()   : token;
            String exchange = instr != null ? instr.getExchange()  : "—";
            boolean enabled = instr != null && instr.isEnabled();

            LocalDate firstDate  = firstTs  != null ? firstTs.atZone(IST).toLocalDate()  : null;
            LocalDate latestDate = latestTs != null ? latestTs.atZone(IST).toLocalDate() : null;

            long daysBehind = latestDate != null
                    ? ChronoUnit.DAYS.between(latestDate, today)
                    : 9999L;

            String status = daysBehind <= 1   ? "OK"
                          : daysBehind <= 5   ? "STALE"
                          : daysBehind <= 30  ? "OLD"
                          :                    "MISSING";

            CoverageRow cr = new CoverageRow(symbol, token, exchange, interval,
                    enabled, firstDate, latestDate, count, daysBehind, status);

            coverageMap.computeIfAbsent(token, k -> new LinkedHashMap<>())
                       .put(interval, cr);
        }

        // Build final list — one entry per instrument, sorted by instrument priority
        // then by interval order. Also add MISSING rows for enabled instruments
        // that have NO candles at all.
        List<CoverageRow> rows = new ArrayList<>();

        for (BiasInstrumentEntity instr : instruments) {
            String token = instr.getSymbolToken();
            Map<String, CoverageRow> intervalMap = coverageMap.getOrDefault(token, Map.of());

            if (intervalMap.isEmpty()) {
                // Instrument has no candles at all → one MISSING row
                rows.add(new CoverageRow(
                        instr.getSymbol(), token, instr.getExchange(), "ALL",
                        instr.isEnabled(), null, null, 0L, 9999L, "MISSING"));
            } else {
                // Add one row per interval, in defined order
                for (String iv : INTERVAL_ORDER) {
                    CoverageRow cr = intervalMap.get(iv);
                    if (cr != null) rows.add(cr);
                }
                // Any interval not in INTERVAL_ORDER
                for (Map.Entry<String, CoverageRow> e : intervalMap.entrySet()) {
                    if (!INTERVAL_ORDER.contains(e.getKey())) rows.add(e.getValue());
                }
            }
        }

        // Stats
        long total   = rows.stream().filter(r -> !r.intervalType().equals("ALL")).count();
        long ok      = rows.stream().filter(r -> "OK".equals(r.status())).count();
        long stale   = rows.stream().filter(r -> "STALE".equals(r.status())).count();
        long old     = rows.stream().filter(r -> "OLD".equals(r.status())).count();
        long missing = rows.stream().filter(r -> "MISSING".equals(r.status())).count();

        model.addAttribute("rows",    rows);
        model.addAttribute("total",   total);
        model.addAttribute("ok",      ok);
        model.addAttribute("stale",   stale);
        model.addAttribute("old",     old);
        model.addAttribute("missing", missing);
        model.addAttribute("today",   today);

        return "coverage/index";
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        // MySQL datetime → java.sql.Timestamp
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }
}
