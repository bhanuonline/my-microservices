package com.example.backoffice.controller;

import com.example.backoffice.config.BackendProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Platform tab — just a link grid out to the external observability tools
 * and a stub audit log page (populated once a real audit event store exists).
 */
@Controller
public class PlatformController {

    private final BackendProperties props;
    public PlatformController(BackendProperties props) { this.props = props; }

    @GetMapping("/observability")
    public String observability(Model model) {
        model.addAttribute("activeNav", "observability");
        model.addAttribute("grafanaUrl",    props.getGrafanaUrl());
        model.addAttribute("prometheusUrl", props.getPrometheusUrl());
        model.addAttribute("zipkinUrl",     props.getZipkinUrl());
        return "observability/index";
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("activeNav", "audit");
        return "audit/index";
    }
}
