package com.angle.trading.report;

import com.angle.trading.config.ReportsProperties;
import com.angle.trading.paper.persistence.SessionEntity;
import com.angle.trading.paper.persistence.SessionRepository;
import com.angle.trading.paper.persistence.TradeEntity;
import com.angle.trading.paper.persistence.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates the daily analysis report — pulls today's sessions + trades from
 * the DB, renders Markdown via {@link DailyReportWriter}, writes to disk.
 *
 * Triggers:
 *   1. Scheduled — auto at market close IST (default 15:35 Mon–Fri)
 *   2. On-demand — via {@link #writeForToday()} exposed by DailyReportController
 *
 * File path: {@code <outputDir>/YYYY-MM-DD.md}, atomic write via .tmp + rename.
 *
 * Idempotent: writing the same day twice overwrites — safe to re-run.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ReportsProperties props;
    private final SessionRepository sessionRepo;
    private final TradeRepository tradeRepo;
    private final DailyReportWriter writer;

    /** Auto-write at market close (default 15:35 IST Mon–Fri). */
    @Scheduled(cron = "#{@reportsProperties.closeCron}", zone = "Asia/Kolkata")
    public void scheduledWrite() {
        if (!props.isEnabled() || !props.isAutoWriteAtClose()) return;
        try {
            Path written = writeForToday();
            log.info("Daily report auto-written: {}", written);
        } catch (Exception e) {
            log.warn("Daily report auto-write failed: {}", e.getMessage());
        }
    }

    /** Write today's report and return the file path. */
    public Path writeForToday() throws IOException {
        return writeForDate(LocalDate.now(IST));
    }

    /** Write a report for a specific date and return the file path. */
    public Path writeForDate(LocalDate date) throws IOException {
        List<SessionEntity> sessions = sessionsForDate(date);
        Map<String, List<TradeEntity>> tradesBySession = sessions.stream()
                .collect(Collectors.toMap(
                        SessionEntity::getId,
                        s -> tradeRepo.findBySessionIdOrderByEntryTimeAsc(s.getId())
                ));

        String markdown = writer.render(date, sessions, tradesBySession);

        Path dir = Path.of(props.getOutputDir());
        Files.createDirectories(dir);
        Path target = dir.resolve(date + ".md");
        Path tmp    = dir.resolve(date + ".md.tmp");
        Files.writeString(tmp, markdown, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        log.info("Daily report written to {}", target.toAbsolutePath());
        return target;
    }

    /** Render markdown without writing to disk — used by the GET endpoint. */
    public String renderForDate(LocalDate date) {
        List<SessionEntity> sessions = sessionsForDate(date);
        Map<String, List<TradeEntity>> tradesBySession = sessions.stream()
                .collect(Collectors.toMap(
                        SessionEntity::getId,
                        s -> tradeRepo.findBySessionIdOrderByEntryTimeAsc(s.getId())
                ));
        return writer.render(date, sessions, tradesBySession);
    }

    private List<SessionEntity> sessionsForDate(LocalDate date) {
        // Sessions started on the given IST calendar day.
        ZonedDateTime start = date.atStartOfDay(IST);
        ZonedDateTime end   = start.plusDays(1);
        return sessionRepo.findAllByOrderByStartedAtDesc().stream()
                .filter(s -> !s.getStartedAt().isBefore(start.toInstant())
                          &&  s.getStartedAt().isBefore(end.toInstant()))
                .sorted((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                .toList();
    }
}
