package com.example.shop.controller;

import com.example.shop.cart.Cart;
import com.example.shop.client.GatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

/**
 * Checkout flow — the moment the whole stack lights up:
 *
 *   GET  /checkout                  review page + place-order button
 *   POST /checkout                  fires POST /api/v1/orders with Idempotency-Key,
 *                                    redirect to /orders/confirm?id=…
 *   GET  /orders/confirm?id=…       polls the order while the saga completes
 *
 * Idempotency-Key is pinned per cart line in the HttpSession so a double
 * click, back-button retry, or network blip doesn't create duplicates —
 * exactly what Tier 1 built.
 */
@Controller
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private static final String IDEM_KEY_ATTR = "shop.ui.idempotencyKey";

    private final Cart cart;
    private final GatewayClient gateway;

    public CheckoutController(Cart cart, GatewayClient gateway) {
        this.cart = cart;
        this.gateway = gateway;
    }

    @GetMapping("/checkout")
    public String review(Model model, HttpSession session) {
        if (cart.isEmpty()) return "redirect:/cart";
        // Pin the key for THIS checkout attempt; cleared on successful submit.
        String key = (String) session.getAttribute(IDEM_KEY_ATTR);
        if (key == null) {
            key = UUID.randomUUID().toString();
            session.setAttribute(IDEM_KEY_ATTR, key);
        }
        model.addAttribute("cart", cart);
        model.addAttribute("idempotencyKey", key);
        return "checkout/review";
    }

    @PostMapping("/checkout")
    public String placeOrder(Model model, HttpSession session) {
        if (cart.isEmpty()) return "redirect:/cart";

        Cart.Line line = cart.firstLine();
        Long productId;
        try {
            productId = Long.parseLong(line.getProductId());
        } catch (NumberFormatException e) {
            log.warn("non-numeric productId in cart line: {}", line.getProductId());
            return "redirect:/cart";
        }

        // Reuse the pinned key for the full browser attempt.
        String key = (String) session.getAttribute(IDEM_KEY_ATTR);
        if (key == null) key = UUID.randomUUID().toString();
        String correlationId = "shop-" + System.currentTimeMillis() + "-" + key.substring(0, 8);

        JsonNode created = gateway.placeOrder(productId, line.getQty(), key, correlationId);
        String orderId = created != null ? created.path("id").asText(null) : null;
        if (orderId == null) {
            model.addAttribute("error", "order-service did not return an id");
            return "checkout/failed";
        }

        log.info("placed order id={} key={} corrId={}", orderId, key, correlationId);
        cart.clear();
        session.removeAttribute(IDEM_KEY_ATTR);

        return "redirect:/orders/confirm?id=" + orderId;
    }

    @GetMapping("/orders/confirm")
    public String confirm(@RequestParam String id, Model model) {
        JsonNode order = gateway.getOrder(id);
        model.addAttribute("orderId", id);
        model.addAttribute("order", order);
        model.addAttribute("status", GatewayClient.statusOf(order));
        return "checkout/confirm";
    }
}
