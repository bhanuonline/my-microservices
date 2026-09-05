package com.angle.trading.controller;

import com.angle.trading.report.DailyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * On-demand daily report endpoints.
 *
 *   POST /api/reports/today/finalize          → write today's file to disk
 *   POST /api/reports/{yyyy-MM-dd}/finalize   → write a specific day's file
 *   GET  /api/reports/today                   → render today's report as markdown (no write)
 *   GET  /api/reports/{yyyy-MM-dd}            → render a specific day's report
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class DailyReportController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DailyReportService reportService;

    @PostMapping("/today/finalize")
    public Map<String, String> writeToday() throws IOException {
        Path path = reportService.writeForToday();
        return Map.of(
                "status", "ok",
                "date",   LocalDate.now(IST).toString(),
                "path",   path.toAbsolutePath().toString()
        );
    }

    @PostMapping("/{date}/finalize")
    public Map<String, String> writeForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) throws IOException {
        Path path = reportService.writeForDate(date);
        return Map.of(
                "status", "ok",
                "date",   date.toString(),
                "path",   path.toAbsolutePath().toString()
        );
    }

    @GetMapping(value = "/today", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> renderToday() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(reportService.renderForDate(LocalDate.now(IST)));
    }

    @GetMapping(value = "/{date}", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> renderForDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(reportService.renderForDate(date));
    }
}
