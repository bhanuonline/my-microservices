package com.example.shop.controller;

import com.example.shop.cart.Cart;
import com.example.shop.client.GatewayClient;
import com.example.shop.dto.ProductView;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PDP + add-to-cart. GET goes to product-service (SQL + Redis cache) so repeat
 * loads of the same product show the cache hit in product-service logs.
 */
@Controller
public class ProductDetailController {

    private final GatewayClient gateway;
    private final Cart cart;

    public ProductDetailController(GatewayClient gateway, Cart cart) {
        this.gateway = gateway;
        this.cart = cart;
    }

    @GetMapping("/products/{id}")
    public String detail(@PathVariable String id, Model model) {
        Long numericId = parseIdOrNull(id);
        if (numericId == null) return "redirect:/products";
        ProductView product = gateway.getProduct(numericId);
        if (product == null) {
            model.addAttribute("missingId", id);
            return "product/not-found";
        }
        model.addAttribute("product", product);
        return "product/detail";
    }

    /**
     * HTMX endpoint — returns a partial fragment that gets swapped into the
     * cart badge. Non-HTMX callers get redirected to /cart.
     */
    @PostMapping("/cart/add")
    public Object addToCart(@RequestParam String productId,
                            @RequestParam(defaultValue = "1") int qty,
                            @org.springframework.web.bind.annotation.RequestHeader(value = "HX-Request", required = false) String hxRequest) {
        Long numericId = parseIdOrNull(productId);
        if (numericId == null) return "redirect:/products";

        ProductView p = gateway.getProduct(numericId);
        if (p == null) return "redirect:/products";
        cart.add(p.id(), p.name(), p.price() != null ? p.price() : 0.0, Math.max(1, qty));

        if (hxRequest != null) {
            // Return just the updated badge span so HTMX swaps it inline.
            String html = "<span>Cart (" + cart.totalItems() + ") — added " + p.name() + "</span>";
            return ResponseEntity.ok().header("Content-Type", "text/html").body(html);
        }
        return "redirect:/cart";
    }

    private static Long parseIdOrNull(String s) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}
