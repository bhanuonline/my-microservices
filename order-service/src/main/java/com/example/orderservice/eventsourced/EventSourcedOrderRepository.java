package com.example.orderservice.eventsourced;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Load / save path for event-sourced {@link OrderAggregate}.
 *
 *   load(orderId)
 *     ├─ latest snapshot? → reconstruct from snapshot state
 *     ├─ replay all events since snapshot.version
 *     └─ return hydrated aggregate
 *
 *   save(agg, newEvents)
 *     ├─ append newEvents (unique(order_id, version) → optimistic concurrency)
 *     └─ every SNAPSHOT_EVERY events, take a snapshot
 */
@Component
public class EventSourcedOrderRepository {

    /** Snapshot every N aggregate events. Tune: higher = cheaper writes, longer loads. */
    private static final int SNAPSHOT_EVERY = 50;

    private final OrderEventRepository eventRepo;
    private final OrderSnapshotRepository snapshotRepo;
    private final ObjectMapper mapper;
    private final ObjectWriter stateWriter;
    private final ObjectReader stateReader;

    public EventSourcedOrderRepository(OrderEventRepository eventRepo, OrderSnapshotRepository snapshotRepo) {
        this.eventRepo = eventRepo;
        this.snapshotRepo = snapshotRepo;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .addMixIn(OrderAggregate.Payload.class, PayloadMixin.class);
        this.stateWriter = mapper.writerFor(OrderAggregate.class);
        this.stateReader = mapper.readerFor(OrderAggregate.class);
    }

    @Transactional(readOnly = true)
    public Optional<OrderAggregate> load(String orderId) {
        Optional<OrderSnapshot> snap = snapshotRepo.findById(orderId);
        OrderAggregate agg;
        int fromVersion;
        if (snap.isPresent()) {
            try {
                agg = stateReader.readValue(snap.get().getState());
                fromVersion = snap.get().getVersion();
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("corrupt snapshot for " + orderId, e);
            }
        } else {
            agg = new OrderAggregate();
            fromVersion = 0;
        }

        List<OrderEvent> events = eventRepo.findByOrderIdAndVersionGreaterThanOrderByVersionAsc(orderId, fromVersion);
        if (events.isEmpty() && snap.isEmpty()) return Optional.empty();

        for (OrderEvent row : events) {
            agg.apply(deserialize(row));
        }
        return Optional.of(agg);
    }

    /**
     * Append newly produced events. The aggregate's current version is the version
     * AFTER the last emitted event is applied — the first new event we write has
     * version = (agg.getVersion() - newEvents.size() + 1), and subsequent ones increment.
     * Collisions on (order_id, version) throw DataIntegrityViolationException which
     * callers should treat as concurrent-modification and retry via re-load.
     */
    @Transactional
    public void save(OrderAggregate agg, List<OrderAggregate.EventPayload> newEvents) {
        if (newEvents.isEmpty()) return;
        int baseVersion = agg.getVersion() - newEvents.size();
        try {
            for (int i = 0; i < newEvents.size(); i++) {
                OrderAggregate.EventPayload ev = newEvents.get(i);
                eventRepo.save(new OrderEvent(agg.getId(), ev.type(), serialize(ev), baseVersion + i + 1));
            }
        } catch (DataIntegrityViolationException concurrentWrite) {
            throw new ConcurrentOrderModificationException(agg.getId(), concurrentWrite);
        }
        // Snapshot heuristic: every SNAPSHOT_EVERY aggregate versions.
        int lastSnapshotVersion = snapshotRepo.findById(agg.getId()).map(OrderSnapshot::getVersion).orElse(0);
        if (agg.getVersion() - lastSnapshotVersion >= SNAPSHOT_EVERY) {
            try {
                snapshotRepo.save(new OrderSnapshot(agg.getId(), agg.getVersion(), stateWriter.writeValueAsString(agg)));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("failed to serialize snapshot for " + agg.getId(), e);
            }
        }
    }

    private String serialize(OrderAggregate.EventPayload ev) {
        try {
            return mapper.writeValueAsString(ev);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event " + ev.type(), e);
        }
    }

    private OrderAggregate.EventPayload deserialize(OrderEvent row) {
        try {
            JavaType t = mapper.getTypeFactory().constructType(OrderAggregate.EventPayload.class);
            return mapper.readValue(row.getPayload(), t);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt event row seq=" + row.getSeq(), e);
        }
    }

    public static class ConcurrentOrderModificationException extends RuntimeException {
        public ConcurrentOrderModificationException(String orderId, Throwable cause) {
            super("concurrent modification detected on order " + orderId, cause);
        }
    }

    /** Teaches Jackson how to serialize the sealed-ish Payload hierarchy with a type tag. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = OrderAggregate.OrderCreatedPayload.class,    name = "OrderCreated"),
            @JsonSubTypes.Type(value = OrderAggregate.PaymentReservedPayload.class, name = "PaymentReserved"),
            @JsonSubTypes.Type(value = OrderAggregate.SimplePayload.class,          name = "Simple"),
            @JsonSubTypes.Type(value = OrderAggregate.ReasonPayload.class,          name = "Reason"),
            @JsonSubTypes.Type(value = OrderAggregate.FulfilledPayload.class,       name = "Fulfilled")
    })
    private abstract static class PayloadMixin {}
}
