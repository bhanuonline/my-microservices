package com.angle.trading.candle;

import com.angle.trading.persistence.BiasInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job dashboard page + REST API.
 *
 *   GET  /jobs                          → dashboard page
 *   POST /api/jobs/candle/run           → trigger all instruments
 *   POST /api/jobs/candle/run/{token}   → trigger single stock
 *   POST /api/jobs/candle/cancel        → cancel running job
 *   POST /api/jobs/candle/retry/{id}    → retry failed/skipped instruments
 *   GET  /api/jobs/candle/status        → live progress JSON
 *   GET  /api/jobs/candle/history       → past runs JSON
 *   GET  /api/jobs/candle/history/{id}/detail → instrument detail for a run
 */
@Controller
@RequiredArgsConstructor
public class CandleJobController {

    private final CandleJobService            service;
    private final CandleJobRunRepository      runRepo;
    private final CandleJobRunDetailRepository detailRepo;
    private final BiasInstrumentRepository    instrumentRepo;

    // ── Dashboard page ────────────────────────────────────────────────

    @GetMapping("/jobs")
    public String page(Model model) {
        List<CandleJobRunEntity> history = runRepo.findTop20ByOrderByStartedAtDesc();
        model.addAttribute("history",     history);
        model.addAttribute("status",      service.status());
        model.addAttribute("instruments", instrumentRepo.findByEnabledTrueOrderByPriorityAscIdAsc());
        return "jobs/index";
    }

    // ── REST API ──────────────────────────────────────────────────────

    @PostMapping("/jobs/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer lookbackDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (service.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "SKIPPED",
                                 "reason", "Job already running",
                                 "currentRunId", service.status().runId()));
        }
        long runId = service.runAll("MANUAL", "admin", interval, lookbackDays, from, to);
        return ResponseEntity.ok(Map.of("ok", true, "runId", runId));
    }

    @PostMapping("/jobs/run/{token}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runSingle(
            @PathVariable String token,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) Integer lookbackDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (service.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "SKIPPED", "reason", "Job already running"));
        }
        long runId = service.runSingle(token, "admin", interval, lookbackDays, from, to);
        if (runId == -1) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "reason", "Token not found: " + token));
        }
        return ResponseEntity.ok(Map.of("ok", true, "runId", runId));
    }

    @PostMapping("/jobs/cancel")
    @ResponseBody
    public Map<String, Object> cancel() {
        if (!service.isRunning()) {
            return Map.of("ok", false, "reason", "No job currently running");
        }
        service.cancel();
        return Map.of("ok", true, "message", "Cancel requested — current instrument will finish");
    }

    @PostMapping("/jobs/retry/{runId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> retry(@PathVariable Long runId) {
        if (service.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "SKIPPED", "reason", "Job already running"));
        }
        long newRunId = service.retry(runId, "admin");
        if (newRunId == -1) {
            return ResponseEntity.ok(Map.of("ok", false, "reason", "Nothing to retry for run " + runId));
        }
        return ResponseEntity.ok(Map.of("ok", true, "runId", newRunId));
    }

    @GetMapping("/jobs/status")
    @ResponseBody
    public Map<String, Object> status() {
        CandleJobService.JobStatus s = service.status();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status",       s.status());
        m.put("runId",        s.runId());
        m.put("startedAt",    s.startedAt());
        m.put("totalCount",   s.totalCount());
        m.put("doneCount",    s.doneCount());
        m.put("failedCount",  s.failedCount());
        m.put("candlesSaved", s.candlesSaved());
        m.put("currentStock", s.currentStock());
        m.put("pctComplete",  s.pctComplete());   // computed — not in record components
        m.put("elapsedSec",   s.elapsedSec());    // computed — not in record components
        return m;
    }

    @GetMapping("/jobs/history")
    @ResponseBody
    public List<CandleJobRunEntity> history(
            @RequestParam(defaultValue = "20") int limit) {
        return runRepo.findTop20ByOrderByStartedAtDesc();
    }

    @GetMapping("/jobs/history/{runId}/detail")
    @ResponseBody
    public List<CandleJobRunDetailEntity> detail(@PathVariable Long runId) {
        return detailRepo.findByRunId(runId);
    }
}
