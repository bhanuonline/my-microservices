package solveproblem.urlshortener.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solveproblem.urlshortener.service.UrlService;

import java.util.Map;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    // POST /api/urls  { "longUrl": "https://example.com/some/very/long/path" }
    @PostMapping
    public ResponseEntity<Map<String, String>> createShortUrl(@RequestBody Map<String, String> body) {
        String longUrl = body.get("longUrl");
        if (longUrl == null || longUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "longUrl is required"));
        }
        String shortCode = urlService.createShortUrl(longUrl);
        return ResponseEntity.ok(Map.of(
                "shortCode", shortCode,
                "shortUrl", "http://localhost:8080/api/urls/" + shortCode
        ));
    }

    // GET /api/urls/{shortCode}  -> returns the original long URL
    @GetMapping("/{shortCode}")
    public ResponseEntity<?> getLongUrl(@PathVariable String shortCode) {
        return urlService.getLongUrl(shortCode)
                .<ResponseEntity<?>>map(longUrl -> ResponseEntity.ok(Map.of("longUrl", longUrl)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "short_code not found")));
    }

    // PUT /api/urls/{shortCode}  { "longUrl": "https://new-destination.com" }
    // INTENTIONALLY BUGGY: updates DB only, leaves Redis stale.
    @PutMapping("/{shortCode}")
    public ResponseEntity<?> updateLongUrlBuggy(@PathVariable String shortCode, @RequestBody Map<String, String> body) {
        String newLongUrl = body.get("longUrl");
        if (newLongUrl == null || newLongUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "longUrl is required"));
        }
        boolean updated = urlService.updateLongUrlBuggy(shortCode, newLongUrl);
        if (!updated) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "short_code not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Updated in DB. Try GET now — watch for stale cache."));
    }
}
