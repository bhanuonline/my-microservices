package com.example.paymentservice.grpc;

import com.example.common.grpc.payment.ChargeRequest;
import com.example.common.grpc.payment.ChargeResponse;
import com.example.common.grpc.payment.PaymentServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Server-side gRPC implementation of {@code PaymentService.Charge}.
 *
 * Business logic is intentionally trivial — this is a demo that the gRPC
 * wiring works. In production this would delegate to the same core
 * PaymentCommandProcessor used by the Kafka saga path, so both transports
 * share a single source of truth.
 */
@Component
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(PaymentGrpcService.class);

    @Override
    public void charge(ChargeRequest req, StreamObserver<ChargeResponse> obs) {
        log.info("gRPC charge orderId={} amount={} idemKey={}",
                req.getOrderId(), req.getAmount(), req.getIdempotencyKey());

        // Toy decision: reject if amount is literally "0", otherwise succeed.
        BigDecimal amount = new BigDecimal(req.getAmount().isBlank() ? "0" : req.getAmount());
        ChargeResponse.Builder resp = ChargeResponse.newBuilder();
        if (amount.signum() <= 0) {
            resp.setStatus(ChargeResponse.Status.FAILED)
                .setFailureReason("non_positive_amount");
        } else {
            resp.setPaymentId("pmt-" + UUID.randomUUID())
                .setStatus(ChargeResponse.Status.SUCCEEDED);
        }
        obs.onNext(resp.build());
        obs.onCompleted();
    }
}
