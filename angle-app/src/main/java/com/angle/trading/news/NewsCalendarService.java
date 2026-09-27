package com.angle.trading.news;

import com.angle.trading.config.NewsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loads NewsEvent entries from a YAML file — classpath resource by default,
 * external file if {@code news.calendar-file} is an absolute path.
 *
 * YAML format:
 *   events:
 *     - name: "RBI Monetary Policy"
 *       dateTime: "2026-10-03 10:00"
 *       severity: HIGH
 *       windowBefore: 30
 *       windowAfter: 60
 *       affectedSymbols: []        # empty = all
 *
 *     - name: "Reliance Earnings"
 *       dateTime: "2026-10-20 16:00"
 *       severity: HIGH
 *       affectedSymbols: ["2885"]  # only this token
 *
 * Thread-safe: the in-memory list is volatile and swapped atomically on reload.
 * Auto-reloads every {@code news.reload-interval-seconds}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCalendarService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NewsProperties props;
    private volatile List<NewsEvent> events = List.of();
    private volatile Instant lastLoadedAt = Instant.EPOCH;
    private volatile String  lastLoadedFrom = "";
    private volatile String  lastError = null;

    @PostConstruct
    void init() {
        reload();
    }

    /** All events, unfiltered. Sorted chronologically. */
    public List<NewsEvent> all() {
        return events;
    }

    /** Events happening now (inside their blackout window). */
    public List<NewsEvent> active() {
        Instant now = Instant.now();
        return events.stream()
                .filter(e -> e.isActive(now, props.getDefaultWindowBefore(), props.getDefaultWindowAfter()))
                .toList();
    }

    /** Future events within the next N days. */
    public List<NewsEvent> upcoming(int days) {
        Instant now = Instant.now();
        Instant cutoff = now.plusSeconds(days * 86400L);
        return events.stream()
                .filter(e -> {
                    Instant t = e.instant();
                    return t.isAfter(now) && t.isBefore(cutoff);
                })
                .toList();
    }

    /**
     * Re-read the calendar file. Called at startup, from scheduled sweeper,
     * and from the admin /api/news/reload endpoint.
     */
    @SuppressWarnings("unchecked")
    public synchronized void reload() {
        Resource resource = resolveResource();
        try (InputStream in = openStream(resource)) {
            if (in == null) {
                events = List.of();
                lastError = "not found: " + resource.getDescription();
                log.warn("News calendar: {}", lastError);
                return;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null || !root.containsKey("events")) {
                events = List.of();
                lastError = "no 'events' key in " + resource.getDescription();
                log.warn("News calendar: {}", lastError);
                return;
            }
            List<Map<String, Object>> raw = (List<Map<String, Object>>) root.get("events");
            List<NewsEvent> parsed = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                try { parsed.add(fromMap(row)); }
                catch (Exception e) { log.warn("News calendar: skipping bad row {}: {}", row, e.getMessage()); }
            }
            parsed.sort(Comparator.comparing(NewsEvent::getDateTime));
            events = parsed;
            lastLoadedAt = Instant.now();
            lastLoadedFrom = resource.getDescription();
            lastError = null;
            log.info("News calendar: loaded {} events from {}", parsed.size(), lastLoadedFrom);
        } catch (Exception e) {
            lastError = "load failed: " + e.getMessage();
            log.warn("News calendar: {}", lastError);
        }
    }

    @Scheduled(fixedRateString = "#{@newsProperties.reloadIntervalSeconds * 1000}")
    public void scheduledReload() {
        if (props.getReloadIntervalSeconds() > 0) reload();
    }

    // ---------- helpers ----------

    private Resource resolveResource() {
        String path = props.getCalendarFile();
        if (path == null || path.isBlank()) path = "news-calendar.yml";
        if (path.startsWith("/") || path.contains(":\\") || Paths.get(path).isAbsolute()) {
            return new FileSystemResource(path);
        }
        return new ClassPathResource(path);
    }

    private static InputStream openStream(Resource r) {
        try {
            if (r instanceof FileSystemResource fs && !Files.exists(Paths.get(fs.getPath()))) return null;
            return r.exists() ? r.getInputStream() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static NewsEvent fromMap(Map<String, Object> row) {
        NewsEvent e = new NewsEvent();
        e.setName(String.valueOf(row.get("name")));
        e.setDateTime(LocalDateTime.parse(String.valueOf(row.get("dateTime")), FMT));
        if (row.get("severity") != null)     e.setSeverity(String.valueOf(row.get("severity")).toUpperCase());
        if (row.get("windowBefore") != null) e.setWindowBefore(((Number) row.get("windowBefore")).intValue());
        if (row.get("windowAfter")  != null) e.setWindowAfter(((Number) row.get("windowAfter")).intValue());
        Object syms = row.get("affectedSymbols");
        if (syms instanceof List<?> list) {
            e.setAffectedSymbols(list.stream().map(String::valueOf).toList());
        }
        return e;
    }

    // ---------- introspection ----------

    public Snapshot snapshot() {
        return new Snapshot(events.size(), lastLoadedAt, lastLoadedFrom, lastError,
                active().size(), upcoming(7).size());
    }

    public record Snapshot(
            int totalEvents,
            Instant lastLoadedAt,
            String  loadedFrom,
            String  error,
            int activeNow,
            int upcoming7d
    ) {}
}
