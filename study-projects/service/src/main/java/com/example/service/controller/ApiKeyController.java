package com.example.service.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class ApiKeyController {

    @GetMapping("/internal/report")
    public String report() {
        log.info("Access report..");
        return "CONFIDENTIAL DATA";
    }
}
