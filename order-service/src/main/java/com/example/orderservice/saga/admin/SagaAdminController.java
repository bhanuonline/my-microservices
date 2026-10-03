package com.example.orderservice.saga.admin;

import com.example.common.saga.RefundCommand;
import com.example.orderservice.saga.OrderSaga;
import com.example.orderservice.saga.OrderSagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator surface for in-flight sagas.
 *
 *   GET  /admin/sagas                → list (optionally filtered by state)
 *   GET  /admin/sagas/{id}           → saga header + ordered step history
 *   POST /admin/sagas/{id}/compensate → force a refund command (manual rescue)
 */
@RestController
@RequestMapping("/admin/sagas")
public class SagaAdminController {

    private static final Logger log = LoggerFactory.getLogger(SagaAdminController.class);
    private static final String REFUND_COMMANDS = "refundCommand-out-0";

    private final OrderSagaRepository sagaRepo;
    private final SagaStepRepository stepRepo;
    private final SagaRecorder recorder;
    private final StreamBridge streamBridge;

    public SagaAdminController(OrderSagaRepository sagaRepo,
                               SagaStepRepository stepRepo,
                               SagaRecorder recorder,
                               StreamBridge streamBridge) {
        this.sagaRepo = sagaRepo;
        this.stepRepo = stepRepo;
        this.recorder = recorder;
        this.streamBridge = streamBridge;
    }

    @GetMapping
    public List<OrderSaga> list(@RequestParam(required = false) OrderSaga.State state) {
        return state == null
                ? sagaRepo.findAll()
                : sagaRepo.findAll().stream().filter(s -> s.getState() == state).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable UUID id) {
        return sagaRepo.findById(id)
                .map(saga -> ResponseEntity.ok(Map.<String, Object>of(
                        "saga", saga,
                        "steps", stepRepo.findBySagaIdOrderByOccurredAtAsc(id)
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/compensate")
    public ResponseEntity<?> forceCompensate(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "operator-triggered") String reason,
                                             @AuthenticationPrincipal Jwt jwt) {
        return sagaRepo.findById(id).map(saga -> {
            if (saga.getPaymentId() == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "no_payment_to_refund",
                        "message", "saga has no recorded paymentId; nothing to compensate"
                ));
            }
            RefundCommand cmd = new RefundCommand(saga.getId(), saga.getOrderId(),
                    saga.getPaymentId(), reason);
            streamBridge.send(REFUND_COMMANDS, cmd);
            recorder.recordCompensation(saga.getId(), "manual.refund", cmd, reason);
            log.warn("manual compensation triggered sagaId={} operator={}", id, operator(jwt));
            return ResponseEntity.ok(Map.of(
                    "status", "compensation_dispatched",
                    "sagaId", id,
                    "paymentId", saga.getPaymentId()
            ));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String operator(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? jwt.getSubject() : "anonymous";
    }
}
