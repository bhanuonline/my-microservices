package com.example.backoffice.controller;

import com.example.backoffice.client.GatewayClient;
import com.example.backoffice.client.OrderQueryClient;
import com.example.backoffice.config.BackendProperties;
import com.example.backoffice.dto.OrderSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Orders admin pages.
 *   /orders         listing (ES)
 *   /orders/live    SSE-powered live feed proxied from notification service
 *   /orders/{id}    detail — order JSON + saga timeline + compensate button
 */
@Controller
public class OrdersController {

    private final OrderQueryClient orderQuery;
    private final GatewayClient gateway;
    private final BackendProperties props;

    public OrdersController(OrderQueryClient orderQuery, GatewayClient gateway,
                            BackendProperties props) {
        this.orderQuery = orderQuery;
        this.gateway = gateway;
        this.props = props;
    }

    @GetMapping("/orders")
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) Long productId,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "25") int size,
                       Model model) {
        List<OrderSummary> orders = orderQuery.list(status, productId, page, size);
        model.addAttribute("activeNav", "orders");
        model.addAttribute("orders", orders);
        model.addAttribute("status", status);
        model.addAttribute("productId", productId);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        return "orders/list";
    }

    @GetMapping("/orders/live")
    public String live(Model model) {
        model.addAttribute("activeNav", "orders-live");
        // Browser connects directly to notification; expose the base URL.
        model.addAttribute("notificationUrl", props.getNotificationUrl());
        return "orders/live";
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable String id, Model model) {
        JsonNode order = gateway.getOrder(id);
        JsonNode sagaDetail = findSagaForOrder(id);

        model.addAttribute("activeNav", "orders");
        model.addAttribute("orderId", id);
        model.addAttribute("order", order);
        model.addAttribute("saga", sagaDetail != null ? sagaDetail.path("saga") : null);
        model.addAttribute("steps", sagaDetail != null ? sagaDetail.path("steps") : null);
        return "orders/detail";
    }

    @PostMapping("/orders/{id}/compensate")
    public String compensate(@PathVariable String id,
                             @RequestParam(defaultValue = "operator-triggered") String reason) {
        // Find the saga for this order and fire its compensate endpoint.
        JsonNode saga = findSagaByOrderId(id);
        if (saga != null) {
            gateway.compensateSaga(saga.path("id").asText(), reason);
        }
        return "redirect:/orders/" + id;
    }

    // ─── helpers ───
    /** Scan /admin/sagas and return the one whose orderId matches. */
    private JsonNode findSagaByOrderId(String orderId) {
        JsonNode arr = gateway.listSagas(null);
        if (arr == null || !arr.isArray()) return null;
        for (JsonNode s : arr) {
            if (orderId.equals(s.path("orderId").asText())) return s;
        }
        return null;
    }

    private JsonNode findSagaForOrder(String orderId) {
        JsonNode saga = findSagaByOrderId(orderId);
        return saga == null ? null : gateway.sagaDetail(saga.path("id").asText());
    }
}
