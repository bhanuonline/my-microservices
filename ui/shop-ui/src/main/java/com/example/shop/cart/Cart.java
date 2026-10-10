package com.example.shop.cart;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HttpSession-scoped shopping cart. One per browser session.
 *
 * Not persisted — logout / server restart wipes it. For a real shop upgrade
 * to Spring Session + Redis so the cart survives across devices and
 * app-server replicas.
 *
 * Keyed by product id (String) because the ES read model uses String ids
 * — avoids a conversion dance when the templates render the cart.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class Cart implements Serializable {

    private final Map<String, Line> lines = new LinkedHashMap<>();

    public void add(String productId, String name, double price, int qty) {
        Line existing = lines.get(productId);
        if (existing != null) {
            existing.qty += qty;
        } else {
            lines.put(productId, new Line(productId, name, price, qty));
        }
    }

    public void updateQty(String productId, int qty) {
        if (qty <= 0) { lines.remove(productId); return; }
        Line line = lines.get(productId);
        if (line != null) line.qty = qty;
    }

    public void remove(String productId) { lines.remove(productId); }

    public void clear() { lines.clear(); }

    public List<Line> getLines() { return new ArrayList<>(lines.values()); }

    public int totalItems() {
        return lines.values().stream().mapToInt(l -> l.qty).sum();
    }

    public double totalPrice() {
        return lines.values().stream().mapToDouble(Line::subtotal).sum();
    }

    public boolean isEmpty() { return lines.isEmpty(); }

    /** First line — temp helper for the single-line checkout path. */
    public Line firstLine() {
        return lines.values().stream().findFirst().orElse(null);
    }

    public static class Line implements Serializable {
        private final String productId;
        private final String name;
        private final double price;
        private int qty;

        public Line(String productId, String name, double price, int qty) {
            this.productId = productId;
            this.name = name;
            this.price = price;
            this.qty = qty;
        }

        public String getProductId() { return productId; }
        public String getName()      { return name; }
        public double getPrice()     { return price; }
        public int getQty()          { return qty; }

        public double subtotal() { return price * qty; }
    }
}
