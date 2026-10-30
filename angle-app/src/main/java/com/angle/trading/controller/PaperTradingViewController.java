package com.angle.trading.controller;

import com.angle.trading.strategy.StrategyRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PaperTradingViewController {

    private final StrategyRegistry strategyRegistry;

    @GetMapping("/paper/sessions")
    public String page(Model model) {
        model.addAttribute("strategies",
                strategyRegistry.availableNames().stream().sorted().toList());
        return "paper/sessions";
    }
}
