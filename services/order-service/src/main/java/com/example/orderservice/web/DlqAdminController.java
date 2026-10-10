package com.example.orderservice.web;

import com.example.common.dlq.DlqEvent;
import com.example.common.dlq.DlqEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operator-facing surface over the DLQ.
 *
 *   GET  /admin/dlq?status=NEW&page=0&size=20  → list
 *   GET  /admin/dlq/{id}                       → single row with full payload
 *   POST /admin/dlq/{id}/replay                → republish to original topic, mark REPLAYED
 *   POST /admin/dlq/{id}/ack                   → mark ACKNOWLEDGED (keep for audit)
 *
 * NB. Secure these endpoints at the gateway (admin scope only). The current
 * repo gates with HTTP basic admin:admin123 — fine for dev.
 */
@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {

    private final DlqEventRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public DlqAdminController(DlqEventRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @GetMapping
    public Page<DlqEvent> list(
            @RequestParam(required = false) DlqEvent.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(page, size);
        return status == null ? repo.findAll(pr) : repo.findByStatus(status, pr);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DlqEvent> get(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<?> replay(@PathVariable Long id,
                                    @AuthenticationPrincipal Jwt jwt) {
        return repo.findById(id).map(ev -> {
            if (ev.getOriginalTopic() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "no_original_topic",
                        "message", "DLQ row lacks x-original-topic header; cannot auto-replay"
                ));
            }
            // Republish to the original topic. Consumers will see the same payload again;
            // consumer idempotency (IdempotencyGuard) deduplicates by eventId.
            kafka.send(ev.getOriginalTopic(), ev.getMessageKey(), ev.getPayload());
            ev.markReplayed(operator(jwt));
            repo.save(ev);
            return ResponseEntity.ok(Map.of(
                    "status", "replayed",
                    "topic", ev.getOriginalTopic(),
                    "id", id
            ));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<?> acknowledge(@PathVariable Long id,
                                         @AuthenticationPrincipal Jwt jwt) {
        return repo.findById(id).map(ev -> {
            ev.markAcknowledged(operator(jwt));
            repo.save(ev);
            return ResponseEntity.ok(Map.of("status", "acknowledged", "id", id));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String operator(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? jwt.getSubject() : "anonymous";
    }
}
