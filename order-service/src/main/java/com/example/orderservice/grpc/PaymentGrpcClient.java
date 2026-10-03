package com.example.orderservice.grpc;

import com.example.common.grpc.payment.ChargeRequest;
import com.example.common.grpc.payment.ChargeResponse;
import com.example.common.grpc.payment.PaymentServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * gRPC client to payment-service. Keep the channel long-lived (HTTP/2 is
 * supposed to be reused across RPCs) and the stub stateless.
 *
 * Call site example:
 *
 *   @Autowired PaymentGrpcClient grpc;
 *   ChargeResponse r = grpc.charge(orderId, "9.99", idemKey);
 *
 * Not wired into OrderService.create yet — this task adds the capability;
 * swapping the REST/Kafka path is a follow-up decision (A/B via feature flag).
 */
@Component
public class PaymentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcClient.class);

    private final String host;
    private final int port;
    private ManagedChannel channel;
    private PaymentServiceGrpc.PaymentServiceBlockingStub stub;

    public PaymentGrpcClient(@Value("${grpc.client.payment.host:localhost}") String host,
                             @Value("${grpc.client.payment.port:9091}") int port) {
        this.host = host;
        this.port = port;
    }

    @PostConstruct
    public void init() {
        // plaintext for dev; mTLS in prod (interview talking point).
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        stub = PaymentServiceGrpc.newBlockingStub(channel);
        log.info("gRPC payment channel initialised → {}:{}", host, port);
    }

    public ChargeResponse charge(String orderId, String amount, String idempotencyKey) {
        ChargeRequest req = ChargeRequest.newBuilder()
                .setOrderId(orderId)
                .setAmount(amount)
                .setCurrency("USD")
                .setIdempotencyKey(idempotencyKey)
                .build();
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS).charge(req);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) channel.shutdown();
    }
}
