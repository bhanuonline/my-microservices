package com.example.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    // Named executor — @Async("eventExecutor") routes tasks here.
    // Having a named executor (vs the default) lets you tune each pool independently
    // and see the thread name in logs: "event-async-1", "event-async-2", etc.
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
}
