package com.angle.trading.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ResearchViewController {

    @GetMapping("/research")
    public String page() {
        return "research/index";
    }
}
