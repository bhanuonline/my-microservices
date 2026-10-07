package com.example.backoffice.controller;

import com.example.backoffice.client.GatewayClient;
import com.example.backoffice.client.OrderQueryClient;
import com.example.backoffice.client.ProductQueryClient;
import com.example.backoffice.client.PrometheusClient;
import com.example.backoffice.dto.DashboardMetrics;
import com.example.backoffice.dto.OrderSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Dashboard = headline KPIs + top recent orders.
 * KPIs come from Prometheus if up; counts fall back to the admin endpoints
 * so the dashboard still works when metrics are offline.
 */
@Controller
public class DashboardController {

    private final PrometheusClient prom;
    private final GatewayClient gateway;
    private final OrderQueryClient orderQuery;
    private final ProductQueryClient productQuery;

    public DashboardController(PrometheusClient prom, GatewayClient gateway,
                               OrderQueryClient orderQuery, ProductQueryClient productQuery) {
        this.prom = prom;
        this.gateway = gateway;
        this.orderQuery = orderQuery;
        this.productQuery = productQuery;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("metrics", gather());
        model.addAttribute("recentOrders", recentOrders());
        return "dashboard";
    }

    private DashboardMetrics gather() {
        Long sagasStarted  = countSagas(gateway.listSagas(null));
        Long sagasFailed   = countSagas(gateway.listSagas("FAILED"));
        Long sagasInFlight = countSagas(gateway.listSagas("STARTED"))
                           + countSagas(gateway.listSagas("PAID"))
                           + countSagas(gateway.listSagas("COMPENSATING"));
        Long dlqPending    = countArray(gateway.listDlq("NEW"));
        long products      = productQuery.totalCount();

        Long ordersToday = prom.queryLong(
                "sum(increase(http_server_requests_seconds_count{application=\"order-service\",uri=\"/api/v1/orders\",method=\"POST\",status=\"201\"}[24h]))");
        Double p95 = prom.query(
                "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket[5m])))");
        Double p95Ms = p95 == null ? null : p95 * 1000.0;

        return new DashboardMetrics(
                ordersToday,
                null,                               // revenue — needs an event stream or ledger
                sagasStarted,
                sagasInFlight,
                sagasFailed,
                dlqPending,
                products,
                p95Ms
        );
    }

    private List<OrderSummary> recentOrders() {
        try {
            return orderQuery.list(null, null, 0, 8);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static long countSagas(JsonNode arr) {
        return arr == null || !arr.isArray() ? 0 : arr.size();
    }

    private static long countArray(JsonNode node) {
        if (node == null) return 0;
        if (node.isArray()) return node.size();
        JsonNode content = node.path("content");
        if (content.isArray()) return content.size();
        JsonNode total = node.path("totalElements");
        return total.isMissingNode() ? 0 : total.asLong(0);
    }
}
