package com.example.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DisplayController {
    @GetMapping("/display")
    public String display(@RequestParam String msg) {
        return "<h1>" + msg + "</h1>";
    }
}