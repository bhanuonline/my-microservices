package com.example.client;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;

@Controller
public class ClientController {

    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/")
    public String home(@RegisteredOAuth2AuthorizedClient("demo-client") OAuth2AuthorizedClient authorizedClient) {
        String result = webClient.get()
                .uri("http://127.0.0.1:8096/api/hello")
                .headers(h -> h.setBearerAuth(authorizedClient.getAccessToken().getTokenValue()))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return "redirect:/display?msg=" + result;
    }
}