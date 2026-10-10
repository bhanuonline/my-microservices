package com.example.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root landing controller.
 *
 * Historical note: this class previously demonstrated calling a resource-server
 * from the browser after login (took an {@code OAuth2AuthorizedClient} arg).
 * That behaviour belongs in a *client* app, not in the authorization server
 * itself — Spring tried to bind the arg from the query string and threw
 * "No primary or single unique constructor" on plain /-hits.
 *
 * Kept as a simple redirect so the file survives for future reference.
 */
@Controller
public class ClientController {

    @GetMapping("/")
    public String home() {
        return "redirect:/admin";
    }
}
