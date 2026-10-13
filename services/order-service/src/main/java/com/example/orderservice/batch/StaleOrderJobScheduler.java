package com.example.orderservice.batch;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class StaleOrderJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderJobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job staleOrderCleanupJob;

    // APPROACH 2 — ScheduledExecutorService (see bottom of class)
    private final ScheduledExecutorService scheduledExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "batch-scheduler");
                t.setDaemon(true);   // won't prevent JVM shutdown
                return t;
            });

    public StaleOrderJobScheduler(JobLauncher jobLauncher, Job staleOrderCleanupJob) {
        this.jobLauncher = jobLauncher;
        this.staleOrderCleanupJob = staleOrderCleanupJob;
    }

    // -------------------------------------------------------------------------
    // APPROACH 1 (original) — @Scheduled (Spring-managed)
    // -------------------------------------------------------------------------
    // Simple, declarative. Spring handles the thread.
    // fixedDelay = wait N ms AFTER the previous run finishes → no overlap possible.
    // fixedRate  = start every N ms regardless → overlap if job takes too long.
    //
    // Limitation: no programmatic control (can't pause/resume/cancel at runtime).
    // -------------------------------------------------------------------------
    @Scheduled(fixedDelayString = "${orderservice.batch.stale-order.fixed-delay-ms:300000}")
    public void run() {
        runJob("@Scheduled");
    }

    // -------------------------------------------------------------------------
    // APPROACH 2 — ScheduledExecutorService (programmatic control)
    // -------------------------------------------------------------------------
    // More control than @Scheduled:
    //   scheduleWithFixedDelay → gap between runs (same as fixedDelay above)
    //   scheduleAtFixedRate    → consistent start times (overlap if job is slow)
    //   future.cancel()        → cancel a specific run at runtime
    //   awaitTermination()     → graceful shutdown waits for in-flight run
    //
    // Currently DISABLED (commented out in @PostConstruct) to avoid running both.
    // To switch: comment out @Scheduled above + uncomment startScheduledExecutor().
    // -------------------------------------------------------------------------
    @PostConstruct
    public void startScheduledExecutor() {
        // -- DISABLED: uncomment to switch from @Scheduled to ScheduledExecutorService --
        //
        // scheduledExecutor.scheduleWithFixedDelay(
        //     () -> runJob("ScheduledExecutorService"),
        //     0,           // initial delay
        //     300,         // delay between runs
        //     TimeUnit.SECONDS
        // );
        // log.info("Batch scheduler started via ScheduledExecutorService");
    }

    @PreDestroy
    public void stopScheduledExecutor() {
        scheduledExecutor.shutdown();
        try {
            // Wait up to 60s for any in-flight batch run to finish cleanly
            if (!scheduledExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
                log.warn("Batch scheduler forced shutdown after 60s timeout");
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Shared run logic used by both approaches
    private void runJob(String triggeredBy) {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        try {
            JobExecution exec = jobLauncher.run(staleOrderCleanupJob, params);
            log.info("[{}] staleOrderCleanupJob completed: status={} duration={}ms",
                    triggeredBy,
                    exec.getStatus(),
                    exec.getEndTime() != null && exec.getStartTime() != null
                            ? exec.getEndTime().toEpochMilli() - exec.getStartTime().toEpochMilli()
                            : "?");
        } catch (Exception e) {
            log.error("[{}] staleOrderCleanupJob failed to launch", triggeredBy, e);
        }
    }
}
