package com.example.orderservice.task;

import org.springframework.cloud.task.configuration.EnableTask;
import org.springframework.context.annotation.Configuration;

// @EnableTask registers this application as a Spring Cloud Task.
// Every time the app runs, Spring Cloud Task records a TASK_EXECUTION row:
//
//   TASK_EXECUTION table:
//   ┌────┬─────────────────┬──────────────────┬─────────────────┬───────────┐
//   │ ID │ TASK_NAME       │ START_TIME        │ END_TIME        │ EXIT_CODE │
//   ├────┼─────────────────┼──────────────────┼─────────────────┼───────────┤
//   │  1 │ order-service   │ 2026-10-12 10:00  │ 2026-10-12 ...  │ 0         │
//   └────┴─────────────────┴──────────────────┴─────────────────┴───────────┘
//
// This pairs well with Spring Batch — each job run is also a task execution,
// giving you a unified audit trail: who ran what, when, and whether it succeeded.
//
// Spring Cloud Task auto-creates its schema in the same datasource (H2 here).
// No extra config needed for dev; point to a shared DB in production so
// multiple instances write to the same execution history.
@EnableTask
@Configuration
public class BatchTaskConfig {
    // No beans needed — @EnableTask does the wiring automatically.
    // Spring Cloud Task registers a TaskRepository bean and a
    // TaskLifecycleListener that records start/end/exit for every run.
}
