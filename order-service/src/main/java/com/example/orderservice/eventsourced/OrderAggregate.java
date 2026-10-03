package com.example.orderservice.eventsourced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Event-sourced Order aggregate.
 *
 * Lifecycle:
 *   handle(cmd) → returns NEW events (not yet persisted)
 *   apply(event) → mutates in-memory state (used both for live writes and for rehydrating from history)
 *
 * Invariant: every mutation goes through apply(). The command handler never
 * touches state directly — it validates, decides, and emits events. apply()
 * is the only place state changes.
 *
 * version increments after each apply. On save, the version of the LAST emitted
 * event must match (lastKnownVersion + 1 ... lastKnownVersion + n). The DB
 * unique constraint on (orderId, version) catches concurrent writers.
 */
public class OrderAggregate {

    public enum Status { NEW, PAYMENT_RESERVED, PAID, PAYMENT_FAILED, CANCELLED, FULFILLED }

    private String id;
    private Long productId;
    private Integer quantity;
    private BigDecimal amount;
    private Status status = Status.NEW;
    private int version;

    public OrderAggregate() {}

    @JsonCreator
    public OrderAggregate(
            @JsonProperty("id") String id,
            @JsonProperty("productId") Long productId,
            @JsonProperty("quantity") Integer quantity,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("status") Status status,
            @JsonProperty("version") int version) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.version = version;
    }

    // ─────────────── Command handlers ───────────────

    public List<EventPayload> handleCreate(String id, Long productId, Integer quantity, BigDecimal amount) {
        require(version == 0, "aggregate already created");
        EventPayload ev = new EventPayload(OrderEvent.Type.OrderCreated,
                new OrderCreatedPayload(id, productId, quantity, amount));
        List<EventPayload> emitted = new ArrayList<>();
        emitted.add(ev);
        apply(ev);
        return emitted;
    }

    public List<EventPayload> handleReservePayment(String paymentId) {
        require(status == Status.NEW, "expected NEW, was " + status);
        EventPayload ev = new EventPayload(OrderEvent.Type.PaymentReserved,
                new PaymentReservedPayload(id, paymentId));
        return List.of(applyAndReturn(ev));
    }

    public List<EventPayload> handleCompletePayment() {
        require(status == Status.PAYMENT_RESERVED, "expected PAYMENT_RESERVED, was " + status);
        return List.of(applyAndReturn(new EventPayload(OrderEvent.Type.PaymentCompleted,
                new SimplePayload(id))));
    }

    public List<EventPayload> handleFailPayment(String reason) {
        require(status == Status.NEW || status == Status.PAYMENT_RESERVED,
                "cannot fail payment in state " + status);
        return List.of(applyAndReturn(new EventPayload(OrderEvent.Type.PaymentFailed,
                new ReasonPayload(id, reason))));
    }

    public List<EventPayload> handleCancel(String reason) {
        require(status != Status.FULFILLED, "cannot cancel fulfilled order");
        return List.of(applyAndReturn(new EventPayload(OrderEvent.Type.OrderCancelled,
                new ReasonPayload(id, reason))));
    }

    public List<EventPayload> handleFulfill(String trackingNo) {
        require(status == Status.PAID, "cannot fulfill before PAID, was " + status);
        return List.of(applyAndReturn(new EventPayload(OrderEvent.Type.OrderFulfilled,
                new FulfilledPayload(id, trackingNo))));
    }

    // ─────────────── Event application ───────────────

    public void apply(EventPayload event) {
        switch (event.type()) {
            case OrderCreated -> {
                OrderCreatedPayload p = (OrderCreatedPayload) event.payload();
                this.id = p.orderId();
                this.productId = p.productId();
                this.quantity = p.quantity();
                this.amount = p.amount();
                this.status = Status.NEW;
            }
            case PaymentReserved -> this.status = Status.PAYMENT_RESERVED;
            case PaymentCompleted -> this.status = Status.PAID;
            case PaymentFailed -> this.status = Status.PAYMENT_FAILED;
            case OrderCancelled -> this.status = Status.CANCELLED;
            case OrderFulfilled -> this.status = Status.FULFILLED;
        }
        this.version += 1;
    }

    private EventPayload applyAndReturn(EventPayload ev) { apply(ev); return ev; }

    private static void require(boolean cond, String message) {
        if (!cond) throw new IllegalStateException(message);
    }

    // ─────────────── Accessors ───────────────

    public String getId()         { return id; }
    public Long getProductId()    { return productId; }
    public Integer getQuantity()  { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus()     { return status; }
    public int getVersion()       { return version; }

    // ─────────────── Event payload shapes ───────────────

    public interface Payload {}
    public record EventPayload(OrderEvent.Type type, Payload payload) {}
    public record OrderCreatedPayload(String orderId, Long productId, Integer quantity, BigDecimal amount) implements Payload {}
    public record PaymentReservedPayload(String orderId, String paymentId) implements Payload {}
    public record SimplePayload(String orderId) implements Payload {}
    public record ReasonPayload(String orderId, String reason) implements Payload {}
    public record FulfilledPayload(String orderId, String trackingNo) implements Payload {}
}
