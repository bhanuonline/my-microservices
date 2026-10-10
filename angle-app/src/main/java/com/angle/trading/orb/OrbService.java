package com.angle.trading.orb;

import com.angle.trading.broker.angel.AngelClient;
import com.angle.trading.broker.angel.stream.CandleClosedEvent;
import com.angle.trading.broker.angel.stream.TickEvent;
import com.angle.trading.broker.angel.stream.model.Tick;
import com.angle.trading.broker.model.Candle;
import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.Interval;
import com.angle.trading.persistence.BiasInstrumentEntity;
import com.angle.trading.service.InstrumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opening Range Breakout engine.
 *
 * Per (instrument × timeframe) we maintain one {@link Cell} and run this state machine:
 *
 *   FORMING → (OR window closed)  → WAITING
 *   WAITING → (candle CLOSES above OR_HIGH) → BUY     (terminal)
 *   WAITING → (candle CLOSES below OR_LOW)  → SHORT   (terminal)
 *   WAITING → (session end, no breakout)    → NO_TRADE
 *
 * Breakout is checked on CANDLE CLOSES (not individual ticks) — matches how most
 * trading apps display ORB and avoids false signals from brief intraday wicks.
 * We piggyback on {@link CandleClosedEvent}: our timeframe cell is advanced
 * each time a candle of equal-or-shorter interval closes.
 *
 * Cold-start backfill: if the app starts mid-day, each cell on first access
 * pulls today's 1-minute candles from session_open onwards via {@link MarketDataService}
 * and replays them — so the OR is computed from the REAL opening range, and any
 * breakout that already happened is reflected immediately.
 *
 * Session hours (IST):
 *   NSE/BSE/NFO/BFO/CDS  : 09:15 → 15:30
 *   MCX                   : 09:00 → 23:30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrbService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** Timeframes to evaluate for each instrument. */
    public static final List<Integer> TIMEFRAMES = List.of(5, 15, 30, 60, 75, 125);

    /** ADX/DI calculation period (Wilder's default). */
    public static final int ADX_PERIOD = 14;

    /** Minimum ADX for a trend to be considered "strong" (Wilder standard). */
    public static final BigDecimal ADX_TREND_THRESHOLD = BigDecimal.valueOf(25);

    /** How many trading days of 1m bars to pre-load for ADX history.
     *  125m TF × 14 periods × 2 (for Wilder warm-up) = 3500 one-minute bars needed.
     *  390 trading min/day × 10 days = 3900 bars → 10 is enough; use 15 for safety. */
    public static final int BACKFILL_DAYS = 15;

    private final InstrumentService instrumentService;
    private final AngelClient angelClient;   // direct call for session-aware intraday fetch
    private final OrbSettings settings;      // mutable at runtime from the dashboard

    /** Key: "symbolToken|minutes". Value: live mutable cell state. */
    private final Map<String, Cell> state = new ConcurrentHashMap<>();

    /** Per-instrument backfill lock + "already done today" set, keyed by symbolToken.
     *  Keeps the N-cells-per-instrument from hammering Angel in parallel. */
    private final Map<String, Object> backfillLocks = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> backfilledByInstrument = new ConcurrentHashMap<>();

    // --------------------------------------------------------------------
    //  Event-driven updates
    // --------------------------------------------------------------------

    /**
     * Ticks are only used for display latency ("last seen price") — they do NOT
     * change the ORB signal. Signal transitions happen on candle closes.
     * Kept here so we track the last-known price for the current running bar
     * if we ever want to show it in the UI.
     */
    @EventListener
    public void onTick(TickEvent e) {
        Tick t = e.tick();
        if (t == null || t.symbolToken() == null || t.ltp() == null) return;
        BiasInstrumentEntity ins = findInstrument(t.symbolToken());
        if (ins == null) return;
        // Store LTP on every cell so snapshot() can show live price per card.
        Instant ltpAt = t.exchangeTime();
        for (int minutes : TIMEFRAMES) {
            Cell cell = ensureCell(ins, minutes);
            synchronized (cell) {
                cell.ltp = t.ltp();
                cell.ltpAt = ltpAt;
            }
        }
    }

    /** Advance ORB state on every CLOSED candle (short intervals aggregate up). */
    @EventListener
    public void onCandleClosed(CandleClosedEvent e) {
        if (e == null || e.symbolToken() == null || e.candle() == null) return;
        BiasInstrumentEntity ins = findInstrument(e.symbolToken());
        if (ins == null) return;

        SessionWindow session = sessionFor(ins.getExchange());
        ZonedDateTime barOpen = e.candle().timestamp().atZone(IST);

        for (int minutes : TIMEFRAMES) {
            Cell cell = ensureCell(ins, minutes);
            synchronized (cell) {
                if (!cell.sessionDate.equals(barOpen.toLocalDate())) {
                    cell.reset(barOpen.toLocalDate());
                    // reset() preserves tfBars/adxDi for ADX history. Dropping cell.current
                    // explicitly here guarantees no overnight-gap super-bar when live events
                    // span a session boundary.
                    cell.current = null;
                    backfillIfPossible(cell, ins, session, barOpen.toLocalDate());
                }
                applyClosedCandle(cell, e.candle(), session, barOpen.toLocalDate());
            }
        }
    }

    // --------------------------------------------------------------------
    //  State transitions driven by closed candles
    // --------------------------------------------------------------------

    /**
     * Apply a single closed candle (any interval ≤ our timeframe) to the cell.
     * Candles outside session hours are ignored.
     */
    private void applyClosedCandle(Cell cell, Candle candle, SessionWindow session, LocalDate day) {
        ZonedDateTime barOpen  = candle.timestamp().atZone(IST);
        ZonedDateTime barClose = barOpen.plusMinutes(1);  // we subscribe to 1m candles

        LocalTime openTime  = barOpen.toLocalTime();
        LocalTime closeTime = barClose.toLocalTime();

        // Fully before or after our session? Ignore.
        if (closeTime.isBefore(session.open()) || openTime.isAfter(session.close())) return;

        LocalTime orCloseTime = session.open().plusMinutes(cell.minutes);

        switch (cell.signal) {
            case FORMING -> {
                // Expand OR with any candle whose window OVERLAPS the OR window.
                // Overlap condition: barOpen < orClose AND barClose > session.open
                // (strict openTime < orClose was too narrow — if Angel's first bar
                //  starts at session_open+5m instead of session_open, 5m OR stayed null.)
                boolean overlapsOr = openTime.isBefore(orCloseTime)
                                   && closeTime.isAfter(session.open());
                if (overlapsOr) {
                    if (cell.orHigh == null || candle.high().compareTo(cell.orHigh) > 0) cell.orHigh = candle.high();
                    if (cell.orLow  == null || candle.low().compareTo(cell.orLow)  < 0) cell.orLow  = candle.low();
                }
                // Lock the OR once a candle closes AT or AFTER the OR window end.
                // Also lock defensively if we received a post-OR candle while H/L
                // is still null (means no in-OR candle existed — use this candle's
                // open as both H and L so the state machine can proceed).
                if (!closeTime.isBefore(orCloseTime)) {
                    if (cell.orHigh == null || cell.orLow == null) {
                        cell.orHigh = candle.open();
                        cell.orLow  = candle.open();
                        log.warn("ORB {} / {}m — no in-OR candles returned; using {} as fallback H=L",
                                cell.symbolToken, cell.minutes, candle.open());
                    }
                    cell.orFormedAt = barClose.toInstant();
                    cell.signal = OrbSignal.WAITING;
                }
            }
            case WAITING, BUY, SHORT -> {
                // When to re-evaluate the signal depends on the ORB cadence setting:
                //   TF_BOUNDARY  → only when this cell's own timeframe candle closes
                //                  (15m → 09:30, 09:45, 10:00...). Clean, matches Angel.
                //   EVERY_1M     → every 1-minute close. More responsive, noisier.
                boolean shouldCheck = switch (settings.getOrbCadence()) {
                    case TF_BOUNDARY -> isOnTfBoundary(closeTime, session.open(), cell.minutes);
                    case EVERY_1M    -> true;
                };

                if (shouldCheck && !openTime.isBefore(orCloseTime)
                        && cell.orHigh != null && cell.orLow != null) {
                    BigDecimal close = candle.close();
                    OrbSignal next;
                    if      (close.compareTo(cell.orHigh) > 0) next = OrbSignal.BUY;
                    else if (close.compareTo(cell.orLow)  < 0) next = OrbSignal.SHORT;
                    else                                       next = OrbSignal.WAITING;

                    if (next != cell.signal) {
                        log.info("ORB {} {} / {}m — {}-min candle closed @ {} = {} (OR {}–{}) → {}",
                                next, nameOf(cell), cell.minutes, cell.minutes, closeTime, close,
                                cell.orLow, cell.orHigh,
                                next == OrbSignal.WAITING ? "re-entered range" : "signal flipped");
                        cell.signal = next;
                        if (next == OrbSignal.BUY || next == OrbSignal.SHORT) {
                            cell.breakoutPrice = close;
                            cell.breakoutAt = barClose.toInstant();
                        }
                    }
                }
                // Session over → if still inside range, mark NO_TRADE (terminal).
                if (closeTime.isAfter(session.close()) && cell.signal == OrbSignal.WAITING) {
                    cell.signal = OrbSignal.NO_TRADE;
                }
            }
            case NO_TRADE -> {
                // Terminal — session already ended inside range.
            }
        }

        // ──────── ADX/DI maintenance ────────
        // Aggregate this 1-minute bar into the running TF bar. When the TF boundary
        // is hit (closeTime aligns with cell.minutes since session.open), finalize
        // the TF bar, append to the history buffer, recompute ADX/DI, and derive
        // the combined signal.
        aggregateAndMaybeComputeAdx(cell, candle, session, closeTime);

        // Combined signal = ORB verdict AND ADX/DI agreement.
        // Recomputed every call so the UI reflects the latest state.
        cell.combinedSignal = combinedSignal(cell);
    }

    /**
     * Feed 1m candle into TF accumulator; emit TF bar + recompute ADX on boundaries.
     * Cadence is controlled by {@link OrbSettings#getAdxCadence()}:
     *   TF_BOUNDARY → ADX only recomputed when a TF bar completes.
     *   EVERY_1M    → ADX recomputed every minute using (finalized history +
     *                 the currently-running TF bar treated as if it just closed).
     */
    private void aggregateAndMaybeComputeAdx(Cell cell, Candle oneMinBar,
                                             SessionWindow session, LocalTime closeTime) {
        if (cell.current == null) {
            cell.current = new BarAggregator(oneMinBar);
        } else {
            cell.current.accept(oneMinBar);
        }

        boolean atBoundary = isOnTfBoundary(closeTime, session.open(), cell.minutes);
        if (atBoundary) {
            cell.tfBars.addLast(cell.current.take());
            cell.current = null;
            while (cell.tfBars.size() > ADX_PERIOD * 3) cell.tfBars.pollFirst();
        }

        boolean shouldRecompute = switch (settings.getAdxCadence()) {
            case TF_BOUNDARY -> atBoundary;
            case EVERY_1M    -> true;
        };
        if (!shouldRecompute) return;

        // Build the window to feed ADX. On TF_BOUNDARY path we use tfBars as-is.
        // On EVERY_1M path we append the IN-PROGRESS TF bar (treated as if closed now)
        // so ADX reflects what's happening inside the current period.
        List<Candle> window;
        if (cell.current != null) {
            window = new ArrayList<>(cell.tfBars);
            window.add(cell.current.take());   // ← snapshot of the running bar
        } else {
            window = new ArrayList<>(cell.tfBars);
        }
        cell.adxDi = AdxDiCalculator.compute(window, ADX_PERIOD);
    }

    /**
     * Combined ORB + ADX signal — behavior depends on {@link OrbSettings.CombinationMode}.
     *
     *   REQUIRE_BOTH (default):
     *       BUY   iff ORB=BUY   AND +DI > -DI AND ADX > threshold
     *       SHORT iff ORB=SHORT AND -DI > +DI AND ADX > threshold
     *       else WAITING (except FORMING/NO_TRADE pass through)
     *
     *   ORB_ONLY:
     *       Use ORB verdict as-is. ADX/DI still shown on the card for info only.
     *
     *   ADX_ONLY:
     *       Signal driven by DI alone (ignore OR position):
     *         BUY   iff +DI > -DI AND ADX > threshold
     *         SHORT iff -DI > +DI AND ADX > threshold
     *         else WAITING
     *       FORMING is preserved so the OR hint still shows during window.
     */
    private OrbSignal combinedSignal(Cell cell) {
        BigDecimal threshold = settings.getAdxThreshold();
        return switch (settings.getCombinationMode()) {
            case ORB_ONLY -> cell.signal;
            case REQUIRE_BOTH -> switch (cell.signal) {
                case FORMING, NO_TRADE, WAITING -> cell.signal;
                case BUY -> (cell.adxDi.isBullishDi() && cell.adxDi.isTrending(threshold))
                            ? OrbSignal.BUY : OrbSignal.WAITING;
                case SHORT -> (cell.adxDi.isBearishDi() && cell.adxDi.isTrending(threshold))
                            ? OrbSignal.SHORT : OrbSignal.WAITING;
            };
            case ADX_ONLY -> {
                if (cell.signal == OrbSignal.FORMING || cell.signal == OrbSignal.NO_TRADE) {
                    yield cell.signal;
                }
                if (!cell.adxDi.isTrending(threshold)) yield OrbSignal.WAITING;
                yield cell.adxDi.isBullishDi() ? OrbSignal.BUY
                     : cell.adxDi.isBearishDi() ? OrbSignal.SHORT
                     : OrbSignal.WAITING;
            }
        };
    }

    private String nameOf(Cell cell) { return cell.symbolToken + " (" + cell.exchange + ")"; }

    // --------------------------------------------------------------------
    //  Backfill from historical candles (cold-start mid-day correctness)
    // --------------------------------------------------------------------

    /**
     * On first access today, pull all 1-min candles for THIS INSTRUMENT once
     * (session_open → now) and replay them into every one of its timeframe cells.
     *
     * Why per-instrument instead of per-cell:
     *   - 5 instruments × 6 timeframes = 30 calls → Angel rate-limits, most fail.
     *   - 5 instruments × 1 call = 5 calls → fits comfortably in Angel's budget.
     *
     * Per-instrument lock serialises the 6 cell initialisations so only ONE
     * thread fetches candles while the other 5 wait on the same result.
     * If the fetch fails, we mark backfill done anyway so we don't retry
     * forever — live events will catch up as new candles close.
     */
    private void backfillIfPossible(Cell cell, BiasInstrumentEntity ins, SessionWindow session, LocalDate day) {
        if (cell.backfilled) {
            log.debug("ORB backfill SKIP {} / {}m — already backfilled", ins.getSymbol(), cell.minutes);
            return;
        }

        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(session.open())) {
            cell.backfilled = true;   // pre-market: nothing to fetch, mark done
            log.debug("ORB backfill SKIP {} / {}m — pre-market (now={}, session_open={})",
                    ins.getSymbol(), cell.minutes, now, session.open());
            return;
        }

        String token = ins.getSymbolToken();
        Object lock = backfillLocks.computeIfAbsent(token, k -> new Object());
        log.info("ORB backfill ENTER {} / {}m — day={} now={} instrumentAlreadyDoneToday={}",
                ins.getSymbol(), cell.minutes, day, now,
                day.equals(backfilledByInstrument.get(token)));

        List<Candle> todayMinuteBars;
        synchronized (lock) {
            // Already done this instrument for today? → just replay cached state (no fetch).
            if (!day.equals(backfilledByInstrument.get(token))) {
                todayMinuteBars = fetchTodayCandles(ins, session, day, now);
                if (todayMinuteBars != null) {
                    // Determine effective session open by finding the earliest-time bar
                    // on TODAY's date (not just the first bar of the 15-day window,
                    // which would be the oldest day). Only shift if Angel returned no
                    // pre-open bars for today — matches our OR window to what Angel actually sees.
                    LocalTime effectiveOpen = session.open();
                    LocalTime earliestToday = null;
                    for (Candle c : todayMinuteBars) {
                        var z = c.timestamp().atZone(IST);
                        if (z.toLocalDate().equals(day)) {
                            LocalTime t = z.toLocalTime();
                            if (earliestToday == null || t.isBefore(earliestToday)) earliestToday = t;
                        }
                    }
                    if (earliestToday != null && earliestToday.isAfter(session.open())) {
                        effectiveOpen = earliestToday;
                        log.info("ORB {} — effective session open shifted {} → {} (Angel returned no pre-open bars today)",
                                ins.getSymbol(), session.open(), effectiveOpen);
                    }
                    SessionWindow effectiveSession = new SessionWindow(effectiveOpen, session.close());

                    // Replay into EVERY timeframe cell of this instrument.
                    StringBuilder trace = new StringBuilder();
                    for (int minutes : TIMEFRAMES) {
                        Cell other = ensureCell(ins, minutes);
                        synchronized (other) {
                            if (other.backfilled) {
                                trace.append(" ").append(minutes).append("m=ALREADY(sig=").append(other.signal)
                                     .append(",H=").append(other.orHigh).append(",L=").append(other.orLow).append(")");
                                continue;
                            }
                            int applied = 0, broke = 0;
                            LocalDate currentDate = null;
                            for (Candle c : todayMinuteBars) {
                                LocalDate barDate = c.timestamp().atZone(IST).toLocalDate();

                                // New session day in the historical replay? Reset ORB state
                                // (OR/signal/breakout) so the final state only reflects TODAY's
                                // actual session.
                                //
                                // CRITICAL: also DISCARD the in-progress TF aggregator here.
                                // If we don't, a partial 125m bar from yesterday ending at 15:30
                                // would keep accepting bars from today's 09:15 open — merging a
                                // 17-hour overnight gap into one "candle". That inflates True Range
                                // and gives nonsense ADX / +DI / -DI values.
                                //
                                // tfBars (completed history) is preserved — ADX legitimately
                                // needs multi-day history for 75m/125m warm-up.
                                if (currentDate == null || !currentDate.equals(barDate)) {
                                    other.signal = OrbSignal.FORMING;
                                    other.combinedSignal = OrbSignal.FORMING;
                                    other.orHigh = null;
                                    other.orLow  = null;
                                    other.breakoutPrice = null;
                                    other.breakoutAt    = null;
                                    other.orFormedAt    = null;
                                    other.sessionDate   = barDate;
                                    other.current       = null;   // ← drop partial TF bar from prev day
                                    currentDate = barDate;
                                }

                                OrbSignal before = other.signal;
                                // The effectiveOpen shift is based on the FIRST overall bar
                                // (today's open on Angel). For historical days it's fine to use
                                // the same window — session_open is per-exchange, same every day.
                                applyClosedCandle(other, c, effectiveSession, barDate);
                                applied++;
                                if (other.signal != before) broke++;
                                // Don't short-circuit — flip rule needs every candle to find
                                // the LATEST side-of-OR, not just the first breakout.
                            }
                            other.backfilled = true;
                            trace.append(" ").append(minutes).append("m=").append(other.signal)
                                 .append("(H=").append(other.orHigh).append(",L=").append(other.orLow)
                                 .append(",applied=").append(applied).append(",transitions=").append(broke).append(")");
                        }
                    }
                    backfilledByInstrument.put(token, day);
                    log.info("ORB backfill DONE {} ({}) — bars={} effectiveOpen={}{}",
                            ins.getSymbol(), token, todayMinuteBars.size(), effectiveSession.open(), trace);
                } else {
                    // Fetch failed — mark THIS cell done to prevent retry storm, others may try later
                    cell.backfilled = true;
                }
            } else {
                // Another cell already triggered the fetch; just mark this one done
                cell.backfilled = true;
            }
        }
    }

    /** Returns null on any failure. Keeps backfill call separate so caller can decide what to do. */
    private List<Candle> fetchTodayCandles(BiasInstrumentEntity ins, SessionWindow session, LocalDate day, LocalTime now) {
        try {
            Exchange exchange;
            try { exchange = Exchange.valueOf(ins.getExchange()); }
            catch (Exception e) { exchange = Exchange.NSE; }

            // Fetch BACKFILL_DAYS back so ADX has enough history even for the 125m TF
            // (125m × 14 periods ≈ 1750 one-minute bars → needs multiple days).
            // Session-aware window: NSE 9:15 start, MCX 9:00 start.
            LocalDateTime from = LocalDateTime.of(day.minusDays(BACKFILL_DAYS), session.open());
            LocalDateTime to   = LocalDateTime.of(day, now.isBefore(session.close()) ? now : session.close());

            log.info("ORB backfill FETCH {} ({}) — Angel getCandlesInRange(exchange={}, token={}, interval=ONE_MINUTE, from={}, to={})",
                    ins.getSymbol(), ins.getSymbolToken(), exchange, ins.getSymbolToken(), from, to);

            long t0 = System.nanoTime();
            List<Candle> bars = angelClient.getCandlesInRange(
                    exchange, ins.getSymbolToken(), Interval.ONE_MINUTE, from, to);
            long tookMs = (System.nanoTime() - t0) / 1_000_000;

            if (bars == null || bars.isEmpty()) {
                log.warn("ORB backfill FAIL {} — Angel returned 0 candles in {}ms (window {} → {})",
                        ins.getSymbol(), tookMs, from, to);
                return null;
            }
            Candle first = bars.get(0);
            Candle last = bars.get(bars.size() - 1);
            log.info("ORB backfill FETCH-OK {} — {} candles in {}ms, first={}@{}, last={}@{}",
                    ins.getSymbol(), bars.size(), tookMs,
                    first.timestamp(), first.open(), last.timestamp(), last.close());
            return bars;
        } catch (Exception e) {
            log.warn("ORB backfill EXCEPTION {}: {}", ins.getSymbol(), e.toString());
            return null;
        }
    }

    // --------------------------------------------------------------------
    //  Daily reset — safety net
    // --------------------------------------------------------------------

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    public void dailyReset() {
        state.clear();
        backfilledByInstrument.clear();
        log.info("ORB state reset for new trading day");
    }

    // --------------------------------------------------------------------
    //  Snapshot for UI / API
    // --------------------------------------------------------------------

    public List<OrbInstrumentRow> snapshot() {
        List<OrbInstrumentRow> out = new ArrayList<>();
        LocalDate today = LocalDate.now(IST);
        for (BiasInstrumentEntity ins : instrumentService.listEnabled()) {
            SessionWindow session = sessionFor(ins.getExchange());
            List<OrbCell> cells = new ArrayList<>(TIMEFRAMES.size());
            for (int minutes : TIMEFRAMES) {
                Cell cell = ensureCell(ins, minutes);
                synchronized (cell) {
                    if (!cell.sessionDate.equals(today)) {
                        cell.reset(today);
                    }
                    // Lazy backfill on first snapshot too (not just events) —
                    // handles the case where the dashboard is opened before any tick arrives.
                    backfillIfPossible(cell, ins, session, today);
                    cells.add(cell.toSnapshot(session, today));
                }
            }
            out.add(new OrbInstrumentRow(
                    ins.getSymbolToken(),
                    ins.getSymbol(),
                    ins.getExchange(),
                    session.open().toString(),
                    session.close().toString(),
                    Collections.unmodifiableList(cells)
            ));
        }
        return out;
    }

    // --------------------------------------------------------------------
    //  Helpers
    // --------------------------------------------------------------------

    private Cell ensureCell(BiasInstrumentEntity ins, int minutes) {
        return state.computeIfAbsent(key(ins.getSymbolToken(), minutes),
                k -> new Cell(ins.getSymbolToken(), ins.getExchange(), minutes, LocalDate.now(IST)));
    }

    private BiasInstrumentEntity findInstrument(String symbolToken) {
        for (BiasInstrumentEntity ins : instrumentService.listEnabled()) {
            if (symbolToken.equals(ins.getSymbolToken())) return ins;
        }
        return null;
    }

    private static SessionWindow sessionFor(String exchange) {
        return switch (exchange == null ? "NSE" : exchange.toUpperCase()) {
            case "MCX"           -> new SessionWindow(LocalTime.of(9, 0),  LocalTime.of(23, 30));
            case "NSE", "BSE", "NFO", "BFO", "CDS"
                                  -> new SessionWindow(LocalTime.of(9, 15), LocalTime.of(15, 30));
            default              -> new SessionWindow(LocalTime.of(9, 15), LocalTime.of(15, 30));
        };
    }

    private static String key(String symbolToken, int minutes) {
        return symbolToken + "|" + minutes;
    }

    /**
     * True if {@code closeTime} aligns to a boundary of {@code tfMinutes} measured
     * from {@code sessionOpen}. Used so each cell only acts on its own candle closes.
     *
     * Examples (sessionOpen = 09:15, tfMinutes = 15):
     *    09:30 → true   (15 min since open)
     *    09:45 → true   (30 min since open)
     *    09:31 → false  (16 min since open)
     */
    private static boolean isOnTfBoundary(LocalTime closeTime, LocalTime sessionOpen, int tfMinutes) {
        int minsSinceOpen = (closeTime.getHour() * 60 + closeTime.getMinute())
                          - (sessionOpen.getHour() * 60 + sessionOpen.getMinute());
        if (minsSinceOpen < 0) return false;
        return minsSinceOpen % tfMinutes == 0;
    }

    /** Opening + closing time of a trading session. */
    private record SessionWindow(LocalTime open, LocalTime close) {}

    /** Mutable per-cell state. Access only under synchronized(cell). */
    private static final class Cell {
        final String symbolToken;
        final String exchange;
        final int minutes;
        LocalDate sessionDate;
        OrbSignal signal;              // raw ORB verdict based on OR position
        OrbSignal combinedSignal;      // ORB AND ADX+DI (used by UI headline)
        BigDecimal orHigh;
        BigDecimal orLow;
        BigDecimal breakoutPrice;
        Instant breakoutAt;
        Instant orFormedAt;
        BigDecimal ltp;         // last tick price seen for this instrument
        Instant ltpAt;          // when the last tick arrived
        boolean backfilled;     // set true after we've tried backfill for sessionDate

        /** Rolling buffer of aggregated TF candles (this cell's own timeframe).
         *  Capped at ADX_PERIOD * 3 so Wilder's smoothing always has enough history
         *  without memory bloat. Latest at the end. */
        final Deque<Candle> tfBars = new ArrayDeque<>();

        /** In-progress TF bar — accumulated from incoming 1m bars until the next
         *  TF boundary, then rolled into {@link #tfBars}. */
        private BarAggregator current;

        // Latest ADX/DI (recomputed on every TF-bar close).
        AdxDi adxDi = AdxDi.EMPTY;

        Cell(String symbolToken, String exchange, int minutes, LocalDate sessionDate) {
            this.symbolToken = symbolToken;
            this.exchange = exchange;
            this.minutes = minutes;
            this.sessionDate = sessionDate;
            this.signal = OrbSignal.FORMING;
            this.combinedSignal = OrbSignal.FORMING;
        }

        void reset(LocalDate newDate) {
            this.sessionDate = newDate;
            this.signal = OrbSignal.FORMING;
            this.combinedSignal = OrbSignal.FORMING;
            this.orHigh = null;
            this.orLow = null;
            this.breakoutPrice = null;
            this.breakoutAt = null;
            this.orFormedAt = null;
            // ltp/ltpAt intentionally preserved — reflect latest known price even across resets.
            this.backfilled = false;
            // tfBars/adxDi preserved across resets so ADX stays warm across the midnight boundary.
            this.current = null;
        }

        OrbCell toSnapshot(SessionWindow session, LocalDate today) {
            Instant orClosesAt = ZonedDateTime.of(today, session.open().plusMinutes(minutes), IST).toInstant();
            return new OrbCell(
                    minutes, signal, combinedSignal, orHigh, orLow,
                    breakoutPrice, breakoutAt, orFormedAt, orClosesAt,
                    ltp, ltpAt,
                    adxDi.adx(), adxDi.plusDi(), adxDi.minusDi()
            );
        }
    }

    /**
     * Rolling OHLC accumulator — merges incoming 1m bars into one TF-length bar.
     * Finalised + emitted via {@link #take()} when the TF boundary arrives.
     */
    private static final class BarAggregator {
        final Instant start;
        BigDecimal open;
        BigDecimal high;
        BigDecimal low;
        BigDecimal close;
        long volume;

        BarAggregator(Candle first) {
            this.start  = first.timestamp();
            this.open   = first.open();
            this.high   = first.high();
            this.low    = first.low();
            this.close  = first.close();
            this.volume = first.volume();
        }

        void accept(Candle c) {
            if (c.high().compareTo(high) > 0) high = c.high();
            if (c.low().compareTo(low)   < 0) low  = c.low();
            close  = c.close();
            volume += c.volume();
        }

        Candle take() { return new Candle(start, open, high, low, close, volume); }
    }
}
