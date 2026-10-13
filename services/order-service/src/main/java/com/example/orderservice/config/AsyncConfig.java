package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    // -------------------------------------------------------------------------
    // APPROACH 1 (original) — Platform thread pool (ThreadPoolTaskExecutor)
    // -------------------------------------------------------------------------
    // Fixed pool of OS threads. Each thread costs ~1MB of stack memory.
    // Tuning required: too small → tasks queue; too large → memory pressure.
    // Best for: CPU-bound tasks, thread-affinity, pre-Java-21 apps.
    // -------------------------------------------------------------------------
    @Bean
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);      // always-alive threads
        executor.setMaxPoolSize(5);       // burst ceiling
        executor.setQueueCapacity(100);   // backlog before spawning beyond core
        executor.setThreadNamePrefix("event-async-");
        executor.initialize();
        return executor;
    }

    // -------------------------------------------------------------------------
    // APPROACH 2 — Virtual Threads (Java 21+)
    // -------------------------------------------------------------------------
    // Virtual threads are lightweight (~few KB) and managed by the JVM, not the OS.
    // No pool needed — a new virtual thread is created per task and discarded after.
    // 1,000,000 virtual threads can run simultaneously with minimal memory.
    //
    // Best for: I/O-bound tasks (Feign calls, DB queries, Kafka waits).
    // Thread name in logs: "virtual-event-0", "virtual-event-1", etc.
    //
    // Switch @Async("eventExecutor") to @Async("virtualEventExecutor") to use this.
    //
    // IMPORTANT: Virtual threads and ThreadLocal/synchronized are tricky —
    // avoid long synchronized blocks, use ReentrantLock instead.
    // -------------------------------------------------------------------------
    @Bean
    public Executor virtualEventExecutor() {
        // Each submitted task gets its own fresh virtual thread — no pool, no queue
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
