package com.example.orderservice.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleOrderJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleOrderJobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job staleOrderCleanupJob;

    public StaleOrderJobScheduler(JobLauncher jobLauncher, Job staleOrderCleanupJob) {
        this.jobLauncher = jobLauncher;
        this.staleOrderCleanupJob = staleOrderCleanupJob;
    }

    // Default: every 5 minutes. Override via orderservice.batch.stale-order.fixed-delay-ms.
    @Scheduled(fixedDelayString = "${orderservice.batch.stale-order.fixed-delay-ms:300000}")
    public void run() {
        // Each run needs unique JobParameters — Spring Batch rejects duplicate job+params combos.
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        try {
            JobExecution exec = jobLauncher.run(staleOrderCleanupJob, params);
            log.info("staleOrderCleanupJob completed: status={} duration={}ms",
                    exec.getStatus(),
                    exec.getEndTime() != null && exec.getStartTime() != null
                            ? exec.getEndTime().toEpochMilli() - exec.getStartTime().toEpochMilli()
                            : "?");
        } catch (Exception e) {
            log.error("staleOrderCleanupJob failed to launch", e);
        }
    }
}
