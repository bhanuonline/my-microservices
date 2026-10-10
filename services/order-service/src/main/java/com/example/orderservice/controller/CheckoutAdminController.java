package com.example.orderservice.controller;

import com.example.common.saga.CapturePaymentCommand;
import com.example.common.saga.VoidPaymentCommand;
import com.example.orderservice.saga.OrderSaga;
import com.example.orderservice.saga.OrderSagaRepository;
import com.example.orderservice.saga.admin.SagaRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Operator-driven capture / void endpoints for split-capture providers
 * (Phase 5). These are the only user-facing way to advance a saga stuck
 * in {@code AUTHORIZED} — the orchestrator intentionally waits there until
 * someone (a human, a scheduled job, a shipping-confirmed webhook, etc.)
 * decides to charge the authorization or release it.
 *
 * <h3>Preconditions</h3>
 * Both endpoints verify the saga is in {@code AUTHORIZED} state before
 * publishing the command. 409 Conflict otherwise. The saga's {@code
 * paymentId} field (populated when PaymentAuthorizedReply arrived) is used
 * as the target for the CapturePaymentCommand / VoidPaymentCommand.
 *
 * <h3>Response contract</h3>
 * 202 Accepted on success — the command is published, the actual state
 * transition happens asynchronously when payment-service replies. Clients
 * poll {@code GET /admin/sagas/{sagaId}} to see the result.
 *
 * <h3>Security</h3>
 * {@code /admin/**} is already behind the project's JWT auth. Future work
 * could add a payment-operator role; for now any authenticated caller can
 * trigger either action.
 */
@RestController
@RequestMapping("/admin/orders")
public class CheckoutAdminController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutAdminController.class);
    private static final String CAPTURE_COMMANDS = "capturePaymentCommand-out-0";
    private static final String VOID_COMMANDS = "voidPaymentCommand-out-0";

    private final OrderSagaRepository sagaRepo;
    private final StreamBridge streamBridge;
    private final SagaRecorder recorder;

    public CheckoutAdminController(OrderSagaRepository sagaRepo,
                                   StreamBridge streamBridge,
                                   SagaRecorder recorder) {
        this.sagaRepo = sagaRepo;
        this.streamBridge = streamBridge;
        this.recorder = recorder;
    }

    @PostMapping("/{orderId}/capture")
    public ResponseEntity<?> capture(
            @PathVariable String orderId,
            @RequestBody(required = false) ActionRequest body) {
        OrderSaga saga = sagaRepo.findByOrderId(orderId).orElse(null);
        if (saga == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "saga_not_found", "orderId", orderId));
        }
        if (saga.getState() != OrderSaga.State.AUTHORIZED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "wrong_state",
                    "orderId", orderId,
                    "sagaId", saga.getId().toString(),
                    "state", saga.getState().name(),
                    "expected", "AUTHORIZED"));
        }
        String reason = body != null && body.reason() != null ? body.reason() : "operator_capture";
        CapturePaymentCommand cmd = new CapturePaymentCommand(
                saga.getId(), saga.getPaymentId(), reason);
        streamBridge.send(CAPTURE_COMMANDS, cmd);
        recorder.recordCommand(saga.getId(), "payment.capture", cmd);
        log.info("Capture requested: orderId={} sagaId={} paymentId={} reason={}",
                orderId, saga.getId(), saga.getPaymentId(), reason);
        return ResponseEntity.accepted().body(Map.of(
                "sagaId", saga.getId().toString(),
                "action", "capture_requested",
                "paymentId", saga.getPaymentId()));
    }

    @PostMapping("/{orderId}/void")
    public ResponseEntity<?> voidPayment(
            @PathVariable String orderId,
            @RequestBody(required = false) ActionRequest body) {
        OrderSaga saga = sagaRepo.findByOrderId(orderId).orElse(null);
        if (saga == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "saga_not_found", "orderId", orderId));
        }
        if (saga.getState() != OrderSaga.State.AUTHORIZED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "wrong_state",
                    "orderId", orderId,
                    "sagaId", saga.getId().toString(),
                    "state", saga.getState().name(),
                    "expected", "AUTHORIZED"));
        }
        String reason = body != null && body.reason() != null ? body.reason() : "operator_void";
        VoidPaymentCommand cmd = new VoidPaymentCommand(
                saga.getId(), saga.getPaymentId(), reason);
        streamBridge.send(VOID_COMMANDS, cmd);
        recorder.recordCommand(saga.getId(), "payment.void", cmd);
        log.warn("Void requested: orderId={} sagaId={} paymentId={} reason={}",
                orderId, saga.getId(), saga.getPaymentId(), reason);
        return ResponseEntity.accepted().body(Map.of(
                "sagaId", saga.getId().toString(),
                "action", "void_requested",
                "paymentId", saga.getPaymentId()));
    }

    /** Optional body for both endpoints. {@code reason} is logged + propagated to the command. */
    public record ActionRequest(String reason) {}
}
