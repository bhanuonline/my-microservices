package com.example.backoffice.dto;

/**
 * Headline KPIs rendered on the dashboard. All counts are best-effort —
 * null stays null if the backing endpoint is unreachable so the UI stays sane.
 */
public record DashboardMetrics(
        Long   ordersToday,            // sum(rate(orders_created_total[24h])) via Prometheus
        Double revenueToday,           // not computed yet — placeholder
        Long   sagasStarted,
        Long   sagasInFlight,
        Long   sagasFailed,
        Long   dlqPending,
        Long   productsIndexed,        // from product-query hit count
        Double httpP95Ms               // histogram_quantile across all services
) {}
