package com.angle.trading.analysis;

import com.angle.trading.analysis.model.HolidayEntry;
import com.angle.trading.analysis.model.MarketStatus;
import com.angle.trading.analysis.model.McxSession;
import com.angle.trading.analysis.model.SpecialOpenEntry;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Determines market open/closed status, session hours, next candle time,
 * and timeframe completion for NSE, BSE, NFO, MCX, CDS, BFO exchanges.
 *
 * <p>Reads {@code market-holidays.yml} at startup. To add or toggle a holiday,
 * change {@code enabled} in the YAML file and restart the app — no code change needed.
 *
 * <p>Reusable by any component: bias warmer, candle scheduler, paper trading,
 * live analysis API.
 */
@Slf4j
@Service
public class MarketCalendarService {

    static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ── Session hours per exchange ───────────────────────────────────
    private static final LocalTime NSE_OPEN  = LocalTime.of(9,  15);
    private static final LocalTime NSE_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime MCX_OPEN  = LocalTime.of(9,  0);
    private static final LocalTime MCX_CLOSE = LocalTime.of(23, 30);
    private static final LocalTime CDS_OPEN  = LocalTime.of(9,  0);
    private static final LocalTime CDS_CLOSE = LocalTime.of(17, 0);

    // MCX evening session boundary — used for MORNING_ONLY_CLOSED logic
    private static final LocalTime MCX_EVENING_START = LocalTime.of(17, 0);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── Loaded from market-holidays.yml ────────────────────────────
    /** holiday map: exchange-name(lowercase) → year → list of entries */
    private final Map<String, Map<Integer, List<HolidayEntry>>> holidays = new HashMap<>();

    /** special sessions that override weekend/holiday block (e.g. Muhurat) */
    private final List<SpecialOpenEntry> specialSessions = new ArrayList<>();

    // ── Lifecycle ───────────────────────────────────────────────────

    /**
     * Loads market-holidays.yml at application startup.
     * Logs a warning and continues with empty lists if the file is missing.
     */
    @PostConstruct
    void loadHolidays() {
        try {
            ClassPathResource resource = new ClassPathResource("market-holidays.yml");
            try (InputStream is = resource.getInputStream()) {
                Yaml yaml = new Yaml();
                Map<String, Object> root = yaml.load(is);
                if (root == null) { log.warn("market-holidays.yml is empty"); return; }

                parseExchangeHolidays("nse", root);
                parseExchangeHolidays("mcx", root);
                parseSpecialOpen(root);

                int nseCount = countHolidays("nse");
                int mcxCount = countHolidays("mcx");
                log.info("Market holidays loaded — NSE: {} entries, MCX: {} entries, special: {}",
                        nseCount, mcxCount, specialSessions.size());
            }
        } catch (Exception e) {
            log.warn("Could not load market-holidays.yml — weekends only enforced: {}", e.getMessage());
        }
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Returns the full market status for the given exchange at the given moment.
     * Checks in order: special_open → weekend → holiday → session hours.
     *
     * @param exchange the exchange (NSE, MCX, etc.)
     * @param now      the current IST time
     * @param baseInterval the base candle interval (used for nextCandleAt)
     * @return complete market status including open/closed, session times, nextCandleAt
     */
    public MarketStatus getMarketStatus(Exchange exchange, ZonedDateTime now,
                                         Interval baseInterval) {
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();

        // 1. Check special_open override (e.g. Muhurat Sunday)
        Optional<SpecialOpenEntry> special = findSpecialSession(exchange, today);
        if (special.isPresent()) {
            SpecialOpenEntry s = special.get();
            return buildStatus(exchange, now, baseInterval,
                    s.marketOpen(), s.marketClose(), true,
                    "Special session: " + s.name());
        }

        // 2. Weekend block
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            ZonedDateTime nextOpen = nextTradingDayOpen(exchange, today.plusDays(1));
            return closedStatus("Weekend — market closed.", nextOpen);
        }

        // 3. Holiday check
        Optional<HolidayEntry> holiday = findHoliday(exchange, today);
        if (holiday.isPresent()) {
            HolidayEntry h = holiday.get();
            // MCX partial holidays: only one session closed
            if (exchange == Exchange.MCX && h.session() != null
                    && h.session() != McxSession.FULL_CLOSED) {
                return handleMcxPartialHoliday(h, now, baseInterval, nowTime);
            }
            ZonedDateTime nextOpen = nextTradingDayOpen(exchange, today.plusDays(1));
            return closedStatus(h.name() + " — market closed.", nextOpen);
        }

        // 4. Normal session hours
        LocalTime open  = sessionOpen(exchange);
        LocalTime close = sessionClose(exchange);
        return buildStatus(exchange, now, baseInterval, open, close, false, null);
    }

    /**
     * Returns true if the exchange is currently open for trading.
     *
     * @param exchange the exchange
     * @param now      current IST time
     */
    public boolean isMarketOpen(Exchange exchange, ZonedDateTime now) {
        return getMarketStatus(exchange, now, Interval.FIFTEEN_MINUTE).open();
    }

    /**
     * Calculates whether a given timeframe's candle bar just completed.
     * Purely time-based — no server state needed.
     *
     * <p>A bar "just completed" if:
     * {@code elapsedMinutes % tfMinutes < baseIntervalMinutes}
     *
     * @param tfInterval   the timeframe to check (e.g. ONE_HOUR)
     * @param baseInterval the base polling interval (e.g. FIFTEEN_MINUTE)
     * @param marketOpen   time the market opened today
     * @param now          current IST time
     * @return true if this TF's bar closed in the current base interval window
     */
    public boolean justCompleted(Interval tfInterval, Interval baseInterval,
                                  LocalTime marketOpen, LocalTime now) {
        int elapsed   = (int) Duration.between(marketOpen, now).toMinutes();
        int tfMins    = toMinutes(tfInterval);
        int baseMins  = toMinutes(baseInterval);
        if (elapsed < 0 || tfMins <= 0 || baseMins <= 0) return false;
        return elapsed % tfMins < baseMins;
    }

    /**
     * Counts how many complete bars of the given timeframe have closed since market open.
     *
     * @param tfInterval  the timeframe
     * @param marketOpen  session open time
     * @param now         current IST time
     */
    public int completeBars(Interval tfInterval, LocalTime marketOpen, LocalTime now) {
        int elapsed = (int) Duration.between(marketOpen, now).toMinutes();
        if (elapsed <= 0) return 0;
        return elapsed / toMinutes(tfInterval);
    }

    /**
     * Returns the next trading day after the given date for the exchange.
     * Skips weekends and enabled holidays.
     *
     * @param exchange the exchange
     * @param from     start searching from this date (inclusive)
     */
    public LocalDate nextTradingDay(Exchange exchange, LocalDate from) {
        LocalDate candidate = from;
        for (int i = 0; i < 10; i++) {   // max 10 days forward (handles long holiday runs)
            DayOfWeek dow = candidate.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                Optional<HolidayEntry> h = findHoliday(exchange, candidate);
                // MCX partial holidays are not full holidays
                boolean fullHoliday = h.isPresent() &&
                        (exchange != Exchange.MCX || h.get().session() == McxSession.FULL_CLOSED);
                if (!fullHoliday) return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        log.warn("Could not find next trading day for {} within 10 days of {}", exchange, from);
        return candidate;
    }

    /**
     * Returns a session progress string like "2h 15m into session (34% complete)".
     *
     * @param marketOpen  session open time
     * @param marketClose session close time
     * @param now         current time
     */
    public String sessionProgress(LocalTime marketOpen, LocalTime marketClose, LocalTime now) {
        int totalMins   = (int) Duration.between(marketOpen, marketClose).toMinutes();
        int elapsedMins = (int) Duration.between(marketOpen, now).toMinutes();
        elapsedMins = Math.max(0, Math.min(elapsedMins, totalMins));

        int hours = elapsedMins / 60;
        int mins  = elapsedMins % 60;
        int pct   = totalMins == 0 ? 0 : (elapsedMins * 100 / totalMins);

        return String.format("%dh %dm into session (%d%% complete)", hours, mins, pct);
    }

    /**
     * Converts an Interval enum to its duration in minutes.
     *
     * @param interval the candle interval
     * @return duration in minutes
     */
    public int toMinutes(Interval interval) {
        return switch (interval) {
            case ONE_MINUTE     ->   1;
            case FIVE_MINUTE    ->   5;
            case FIFTEEN_MINUTE ->  15;
            case THIRTY_MINUTE  ->  30;
            case ONE_HOUR       ->  60;
            case TWO_HOUR       -> 120;
            case THREE_HOUR     -> 180;
            case FOUR_HOUR      -> 240;
            case ONE_DAY        -> 390;   // NSE trading minutes per day (6.5h)
        };
    }

    // ── Private helpers ─────────────────────────────────────────────

    /**
     * Builds a MarketStatus for a normally open session.
     * Calculates nextCandleAt, sessionProgress, and open/closed state.
     */
    private MarketStatus buildStatus(Exchange exchange, ZonedDateTime now,
                                      Interval baseInterval,
                                      LocalTime open, LocalTime close,
                                      boolean isSpecial, String note) {
        LocalTime nowTime = now.toLocalTime();
        boolean isOpen    = nowTime.isAfter(open) && nowTime.isBefore(close);

        ZonedDateTime nextCandle = computeNextCandleAt(exchange, baseInterval, now, open, close);
        String progress = isOpen ? sessionProgress(open, close, nowTime) : null;

        String message = isOpen
                ? exchange.name() + " open — session " + open.format(TIME_FMT)
                  + " to " + close.format(TIME_FMT) + " IST"
                : exchange.name() + " session has not started or has ended.";

        return new MarketStatus(
                isOpen,
                open.format(TIME_FMT),
                close.format(TIME_FMT),
                isSpecial ? note + " — " + message : message,
                nextCandle,
                isOpen ? null : nextTradingDayOpen(exchange, now.toLocalDate().plusDays(1)),
                progress,
                note
        );
    }

    /**
     * Builds a closed MarketStatus with the next market open time.
     */
    private MarketStatus closedStatus(String message, ZonedDateTime nextOpen) {
        return new MarketStatus(false, null, null, message, null, nextOpen, null, null);
    }

    /**
     * Handles MCX partial holidays where only one session is closed.
     * Before 17:00 → morning session closed.
     * After  17:00 → evening session open.
     */
    private MarketStatus handleMcxPartialHoliday(HolidayEntry holiday, ZonedDateTime now,
                                                   Interval baseInterval, LocalTime nowTime) {
        return switch (holiday.session()) {
            case MORNING_ONLY_CLOSED -> {
                if (nowTime.isBefore(MCX_EVENING_START)) {
                    // Morning closed — evening opens at 17:00
                    ZonedDateTime eveningOpen = now.toLocalDate()
                            .atTime(MCX_EVENING_START).atZone(IST);
                    yield closedStatus(
                            holiday.name() + " — Morning session closed. "
                            + "Evening session opens at 17:00 IST.",
                            eveningOpen);
                } else {
                    // Evening session is open
                    yield buildStatus(Exchange.MCX, now, baseInterval,
                            MCX_EVENING_START, MCX_CLOSE, false,
                            holiday.name() + " — Evening session only");
                }
            }
            case EVENING_ONLY_CLOSED -> {
                if (nowTime.isBefore(MCX_EVENING_START)) {
                    // Morning session is open
                    yield buildStatus(Exchange.MCX, now, baseInterval,
                            MCX_OPEN, MCX_EVENING_START, false,
                            holiday.name() + " — Morning session only (closes 17:00)");
                } else {
                    // Evening closed
                    ZonedDateTime nextOpen = nextTradingDayOpen(Exchange.MCX,
                            now.toLocalDate().plusDays(1));
                    yield closedStatus(
                            holiday.name() + " — Evening session closed.", nextOpen);
                }
            }
            default -> {
                ZonedDateTime nextOpen = nextTradingDayOpen(Exchange.MCX,
                        now.toLocalDate().plusDays(1));
                yield closedStatus(holiday.name() + " — market closed.", nextOpen);
            }
        };
    }

    /**
     * Computes the exact IST time when the next candle bar closes.
     *
     * <p>Handles the end-of-day edge case: if the next bar would close
     * after market close, jumps to the next trading day's first candle.
     */
    private ZonedDateTime computeNextCandleAt(Exchange exchange, Interval baseInterval,
                                               ZonedDateTime now, LocalTime open,
                                               LocalTime close) {
        LocalTime nowTime  = now.toLocalTime();
        int baseMins       = toMinutes(baseInterval);
        int elapsed        = (int) Duration.between(open, nowTime).toMinutes();
        if (elapsed < 0) elapsed = 0;

        int completedBars  = elapsed / baseMins;
        LocalTime nextBar  = open.plusMinutes((long)(completedBars + 1) * baseMins);

        // End-of-day: next bar is at or after close → jump to next trading day
        if (!nextBar.isBefore(close)) {
            LocalDate nextDay = nextTradingDay(exchange, now.toLocalDate().plusDays(1));
            return nextDay.atTime(sessionOpen(exchange)).atZone(IST);
        }
        return now.toLocalDate().atTime(nextBar).atZone(IST);
    }

    /** Returns the next trading day's market open as a ZonedDateTime. */
    private ZonedDateTime nextTradingDayOpen(Exchange exchange, LocalDate from) {
        LocalDate next = nextTradingDay(exchange, from);
        LocalTime open = sessionOpen(exchange);

        // Special session override on the next day?
        Optional<SpecialOpenEntry> special = findSpecialSession(exchange, next);
        if (special.isPresent()) open = special.get().marketOpen();

        return next.atTime(open).atZone(IST);
    }

    /** Returns the default session open time for an exchange. */
    private LocalTime sessionOpen(Exchange exchange) {
        return switch (exchange) {
            case MCX -> MCX_OPEN;
            case CDS -> CDS_OPEN;
            default  -> NSE_OPEN;
        };
    }

    /** Returns the default session close time for an exchange. */
    private LocalTime sessionClose(Exchange exchange) {
        return switch (exchange) {
            case MCX -> MCX_CLOSE;
            case CDS -> CDS_CLOSE;
            default  -> NSE_CLOSE;
        };
    }

    /**
     * Finds an enabled special session for the given exchange and date.
     *
     * @param exchange the exchange
     * @param date     the date to check
     */
    private Optional<SpecialOpenEntry> findSpecialSession(Exchange exchange, LocalDate date) {
        String ex = exchange.name().toUpperCase();
        return specialSessions.stream()
                .filter(SpecialOpenEntry::enabled)
                .filter(s -> s.date().equals(date))
                .filter(s -> s.exchanges().stream().anyMatch(e -> e.equalsIgnoreCase(ex)))
                .findFirst();
    }

    /**
     * Finds an enabled holiday for the given exchange and date.
     *
     * @param exchange the exchange
     * @param date     the date to check
     */
    private Optional<HolidayEntry> findHoliday(Exchange exchange, LocalDate date) {
        String key = exchangeKey(exchange);
        int year   = date.getYear();
        List<HolidayEntry> list = holidays
                .getOrDefault(key, Collections.emptyMap())
                .getOrDefault(year, Collections.emptyList());

        if (list.isEmpty() && !holidays.containsKey(key)) {
            log.warn("No holiday list for {} year {} — weekends only enforced", exchange, year);
        }
        return list.stream()
                .filter(HolidayEntry::enabled)
                .filter(h -> h.date().equals(date))
                .findFirst();
    }

    /** Maps Exchange enum to YAML key. */
    private String exchangeKey(Exchange exchange) {
        return switch (exchange) {
            case BSE, NFO, BFO -> "nse";   // same holiday list as NSE
            case MCX            -> "mcx";
            default             -> "nse";
        };
    }

    // ── YAML Parsing ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void parseExchangeHolidays(String exchangeKey, Map<String, Object> root) {
        Object exchangeData = root.get(exchangeKey);
        if (!(exchangeData instanceof Map)) return;

        Map<Object, Object> yearMap = (Map<Object, Object>) exchangeData;
        Map<Integer, List<HolidayEntry>> parsed = new HashMap<>();

        for (Map.Entry<Object, Object> entry : yearMap.entrySet()) {
            if (!(entry.getKey() instanceof Integer year)) continue;
            if (!(entry.getValue() instanceof List<?> rawList)) continue;

            List<HolidayEntry> entries = new ArrayList<>();
            for (Object item : rawList) {
                if (!(item instanceof Map<?, ?> map)) continue;
                try {
                    HolidayEntry h = parseHolidayEntry(map);
                    if (h != null) entries.add(h);
                } catch (Exception e) {
                    log.warn("Skipping invalid holiday entry in {}/{}: {}", exchangeKey, year, e.getMessage());
                }
            }
            parsed.put(year, entries);
            log.debug("Loaded {} {} holidays for {}", entries.size(), exchangeKey.toUpperCase(), year);
        }
        holidays.put(exchangeKey, parsed);
    }

    @SuppressWarnings("unchecked")
    private HolidayEntry parseHolidayEntry(Map<?, ?> map) {
        Object dateObj    = map.get("date");
        Object nameObj    = map.get("name");
        Object enabledObj = map.get("enabled");
        Object sessionObj = map.get("session");
        if (dateObj == null) return null;

        // SnakeYAML auto-converts YYYY-MM-DD to java.util.Date — handle both
        LocalDate date   = parseDate(dateObj);
        String name      = nameObj != null ? nameObj.toString() : "Unknown";
        boolean enabled  = enabledObj == null || Boolean.parseBoolean(enabledObj.toString());
        McxSession session = sessionObj != null
                ? McxSession.valueOf(sessionObj.toString().toUpperCase())
                : null;
        return new HolidayEntry(date, name, enabled, session);
    }

    @SuppressWarnings("unchecked")
    private void parseSpecialOpen(Map<String, Object> root) {
        Object data = root.get("special_open");
        if (!(data instanceof List<?> list)) return;

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            try {
                SpecialOpenEntry s = parseSpecialEntry(map);
                if (s != null) specialSessions.add(s);
            } catch (Exception e) {
                log.warn("Skipping invalid special_open entry: {}", e.getMessage());
            }
        }
        log.debug("Loaded {} special_open entries", specialSessions.size());
    }

    @SuppressWarnings("unchecked")
    private SpecialOpenEntry parseSpecialEntry(Map<?, ?> map) {
        Object dateObj     = map.get("date");
        Object nameObj     = map.get("name");
        Object openObj     = map.get("marketOpen");
        Object closeObj    = map.get("marketClose");
        Object enabledObj  = map.get("enabled");
        Object exchObj     = map.get("exchanges");
        if (dateObj == null || openObj == null || closeObj == null) return null;

        LocalDate date     = parseDate(dateObj);
        String name        = nameObj != null ? nameObj.toString() : "Special Session";
        LocalTime open     = LocalTime.parse(openObj.toString());
        LocalTime close    = LocalTime.parse(closeObj.toString());
        boolean enabled    = enabledObj == null || Boolean.parseBoolean(enabledObj.toString());
        List<String> exch  = exchObj instanceof List<?> rawList
                ? rawList.stream().map(Object::toString).toList()
                : List.of("NSE", "BSE", "MCX");
        return new SpecialOpenEntry(date, name, exch, open, close, enabled);
    }

    /**
     * Parses a date from YAML — handles both String ("2026-01-26")
     * and java.util.Date (SnakeYAML auto-converts ISO dates to Date objects).
     */
    private LocalDate parseDate(Object dateObj) {
        if (dateObj instanceof Date d) {
            // SnakeYAML stores dates at midnight UTC — convert to local date
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return LocalDate.parse(dateObj.toString());
    }

    private int countHolidays(String key) {
        return holidays.getOrDefault(key, Collections.emptyMap())
                .values().stream().mapToInt(List::size).sum();
    }
}
