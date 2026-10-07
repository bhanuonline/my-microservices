package com.example.orderservice.saga;

import com.example.common.saga.NotifyUserCommand;
import com.example.common.saga.NotifyUserReply;
import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.RefundCommand;
import com.example.orderservice.model.Order;
import com.example.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests for the order saga against a real Kafka (Testcontainers)
 * and in-memory H2. Covers the four scenarios that matter:
 *
 *   1. Happy path:        payment OK → notify OK → saga NOTIFIED, order PAID.
 *   2. Payment-fail:      payment NOT_OK → saga FAILED, order CANCELLED,
 *                         orders_saga_terminal_total{outcome="failed"}++.
 *   3. Compensation:      payment OK → notify NOT_OK → RefundCommand published,
 *                         saga FAILED (compensated).
 *   4. Boot-time resumer: seed a STARTED saga older than stale-after,
 *                         fire ApplicationReadyEvent, assert the orchestrator
 *                         re-published a PaymentCommand.
 *
 * Design notes:
 *
 *   - ONE KafkaContainer, static, shared by all tests in the class. Cold-start
 *     is ~5s; tolerable once, not four times.
 *   - Each test uses a fresh sagaId. The orchestrator routes replies by sagaId,
 *     so even with shared Kafka bindings there's no cross-test contamination.
 *   - We call OrderSagaOrchestrator.start() DIRECTLY (not the HTTP controller)
 *     because the controller does a Feign call to product-service that isn't
 *     part of what we're testing here. One concern per test.
 *   - Awaitility (not Thread.sleep) for every "wait for X to happen" assertion,
 *     because the reply handler commits a DB tx asynchronously.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
class SagaIntegrationTest {

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry r) {
        String brokers = KAFKA.getBootstrapServers();
        r.add("spring.kafka.bootstrap-servers", () -> brokers);
        r.add("spring.cloud.stream.kafka.binder.brokers", () -> brokers);
    }

    @Autowired OrderSagaOrchestrator orchestrator;
    @Autowired OrderSagaRepository sagaRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired MeterRegistry metrics;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> commandConsumer;

    @BeforeAll
    static void initKafkaClients() {
        Properties pp = new Properties();
        pp.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        pp.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        pp.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producer = new KafkaProducer<>(pp);

        Properties cp = new Properties();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        cp.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-test-command-watcher-" + UUID.randomUUID());
        cp.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cp.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        cp.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        commandConsumer = new KafkaConsumer<>(cp);
        commandConsumer.subscribe(List.of(
                "payment.commands",
                "notification.commands",
                "payment.commands.refund"));
    }

    @AfterAll
    static void tearDownKafkaClients() {
        if (producer != null) producer.close();
        if (commandConsumer != null) commandConsumer.close();
    }

    // ────────────────────────────────────────────────────────────────────
    // 1. HAPPY PATH
    // ────────────────────────────────────────────────────────────────────
    @Test
    void happyPath_paymentOk_notifyOk_sagaNotified() throws Exception {
        String orderId = createOrder(new BigDecimal("49.99"));
        UUID sagaId = sagaIdFor(orderId);

        drainCommand("payment.commands", sagaId);  // orchestrator sent the first command

        publishReply("payment.replies",
                new PaymentReply(sagaId, orderId, true, null, "pay-" + sagaId));

        drainCommand("notification.commands", sagaId);  // next step fired

        publishReply("notification.replies",
                new NotifyUserReply(sagaId, orderId, true, null));

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OrderSaga s = sagaRepo.findById(sagaId).orElseThrow();
                    assertThat(s.getState()).isEqualTo(OrderSaga.State.NOTIFIED);
                    Order o = orderRepo.findById(orderId).orElseThrow();
                    assertThat(o.getStatus()).isEqualTo(Order.Status.PAID);
                });

        assertCounter("orders.saga.terminal", Map.of("outcome", "completed"), 1);
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. PAYMENT FAILS → saga FAILED
    // ────────────────────────────────────────────────────────────────────
    @Test
    void paymentFail_sagaFailed_orderCancelled_metricIncremented() throws Exception {
        String orderId = createOrder(new BigDecimal("10.00"));
        UUID sagaId = sagaIdFor(orderId);

        drainCommand("payment.commands", sagaId);

        publishReply("payment.replies",
                new PaymentReply(sagaId, orderId, false, "insufficient_funds", null));

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OrderSaga s = sagaRepo.findById(sagaId).orElseThrow();
                    assertThat(s.getState()).isEqualTo(OrderSaga.State.FAILED);
                    assertThat(s.getFailureReason()).contains("insufficient_funds");
                    Order o = orderRepo.findById(orderId).orElseThrow();
                    assertThat(o.getStatus()).isEqualTo(Order.Status.CANCELLED);
                });

        assertCounter("orders.saga.terminal", Map.of("outcome", "failed"), 1);
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. NOTIFY FAILS → COMPENSATION (refund fired)
    // ────────────────────────────────────────────────────────────────────
    @Test
    void notifyFail_refundCommandPublished_sagaFailed() throws Exception {
        String orderId = createOrder(new BigDecimal("99.00"));
        UUID sagaId = sagaIdFor(orderId);

        drainCommand("payment.commands", sagaId);
        publishReply("payment.replies",
                new PaymentReply(sagaId, orderId, true, null, "pay-" + sagaId));

        drainCommand("notification.commands", sagaId);
        publishReply("notification.replies",
                new NotifyUserReply(sagaId, orderId, false, "sms_gateway_down"));

        RefundCommand refund = drainCommand("payment.commands.refund", sagaId, RefundCommand.class);
        assertThat(refund.orderId()).isEqualTo(orderId);
        assertThat(refund.paymentId()).isEqualTo("pay-" + sagaId);
        assertThat(refund.reason()).contains("sms_gateway_down");

        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    OrderSaga s = sagaRepo.findById(sagaId).orElseThrow();
                    assertThat(s.getState()).isEqualTo(OrderSaga.State.FAILED);
                    Order o = orderRepo.findById(orderId).orElseThrow();
                    assertThat(o.getStatus()).isEqualTo(Order.Status.CANCELLED);
                });

        assertCounter("orders.saga.terminal", Map.of("outcome", "compensated"), 1);
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. BOOT-TIME RESUMER
    // Seed a STARTED saga with createdAt > stale-after ago, publish an
    // ApplicationReadyEvent, assert the orchestrator re-publishes the
    // payment command for it.
    // ────────────────────────────────────────────────────────────────────
    @Autowired OrderSagaResumer resumer;

    @Test
    @Transactional
    void bootResumer_rePublishesPaymentCommandForStuckStartedSaga() throws Exception {
        BigDecimal amount = new BigDecimal("77.77");
        String orderId = UUID.randomUUID().toString();
        orderRepo.save(new Order(orderId, 1L, 1, amount));

        OrderSaga stuck = new OrderSaga(orderId);
        setFieldToInstantInPast(stuck, "createdAt", 60);  // 60s ago → older than stale-after=PT30S
        sagaRepo.save(stuck);
        UUID sagaId = stuck.getId();

        // Resumer bean is wired but disabled in the test profile
        // (orderservice.saga.resume.enabled=false). Flip it on for just this test
        // via the package-private test seam.
        resumer.setEnabled(true);
        resumer.resumeOnBoot();

        PaymentCommand cmd = drainCommand("payment.commands", sagaId, PaymentCommand.class);
        assertThat(cmd.orderId()).isEqualTo(orderId);
        assertThat(cmd.amount()).isEqualByComparingTo(amount);

        assertCounter("orders.saga.resumed", Map.of("state", "STARTED"), 1);
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    /** Persists an Order + starts a saga via the real orchestrator path. */
    private String createOrder(BigDecimal amount) {
        String orderId = UUID.randomUUID().toString();
        orderRepo.save(new Order(orderId, 1L, 1, amount));
        orchestrator.start(orderId, amount);
        return orderId;
    }

    private UUID sagaIdFor(String orderId) {
        return await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> sagaRepo.findByOrderId(orderId).map(OrderSaga::getId).orElse(null),
                        id -> id != null);
    }

    /** Blocks until a command for the given sagaId lands on the topic. Fails after 20s. */
    private void drainCommand(String topic, UUID sagaId) {
        drainCommand(topic, sagaId, Map.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T drainCommand(String topic, UUID sagaId, Class<T> type) {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            var records = commandConsumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> rec : records) {
                if (!rec.topic().equals(topic)) continue;
                try {
                    T payload = (T) JSON.readValue(rec.value(), type);
                    String payloadSagaId = extractSagaId(payload);
                    if (payloadSagaId != null && payloadSagaId.equals(sagaId.toString())) {
                        return payload;
                    }
                } catch (Exception ignored) { /* not the record we want */ }
            }
        }
        throw new AssertionError("No command for sagaId=" + sagaId + " on topic " + topic + " within 20s");
    }

    private String extractSagaId(Object payload) {
        if (payload instanceof Map<?, ?> m) {
            Object s = m.get("sagaId");
            return s == null ? null : s.toString();
        }
        try {
            var f = payload.getClass().getDeclaredMethod("sagaId");
            Object s = f.invoke(payload);
            return s == null ? null : s.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void publishReply(String topic, Object reply) throws Exception {
        String body = JSON.writeValueAsString(reply);
        producer.send(new ProducerRecord<>(topic, body)).get();
    }

    private void assertCounter(String name, Map<String, String> tags, double atLeast) {
        var meter = metrics.find(name).tags(tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue()))
                .toArray(String[]::new)).counter();
        assertThat(meter)
                .describedAs("meter %s with tags %s not found", name, tags)
                .isNotNull();
        assertThat(meter.count()).isGreaterThanOrEqualTo(atLeast);
    }

    private static void setFieldToInstantInPast(Object target, String field, long secondsAgo) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, Instant.now().minusSeconds(secondsAgo));
        } catch (Exception e) {
            throw new RuntimeException("reflection failed on " + field, e);
        }
    }
}
