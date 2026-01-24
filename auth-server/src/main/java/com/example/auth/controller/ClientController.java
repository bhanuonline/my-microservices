package com.example.auth.controller;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
public class ClientController {

    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/")
    public String home(OAuth2AuthorizedClient authorizedClient) {
        String response = webClient
                .get()
                .uri("http://127.0.0.1:8090/api/hello")
                .headers(headers -> headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return "<h1>" + response + "</h1>";
    }
}