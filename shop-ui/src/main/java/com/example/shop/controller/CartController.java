package com.example.shop.controller;

import com.example.shop.cart.Cart;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CartController {

    private final Cart cart;

    public CartController(Cart cart) { this.cart = cart; }

    @GetMapping("/cart")
    public String view(Model model) {
        model.addAttribute("cart", cart);
        return "cart/view";
    }

    @PostMapping("/cart/update")
    public String updateQty(@RequestParam String productId,
                            @RequestParam int qty) {
        cart.updateQty(productId, qty);
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam String productId) {
        cart.remove(productId);
        return "redirect:/cart";
    }

    @PostMapping("/cart/clear")
    public String clear() {
        cart.clear();
        return "redirect:/cart";
    }
}
