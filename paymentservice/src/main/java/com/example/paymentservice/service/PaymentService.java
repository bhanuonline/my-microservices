package com.example.paymentservice.service;

import com.example.common.saga.PaymentCommand;
import com.example.common.saga.PaymentReply;
import com.example.common.saga.RefundCommand;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.provider.PaymentProvider;
import com.example.paymentservice.provider.PaymentProviderRegistry;
import com.example.paymentservice.provider.ProviderSession;
import com.example.paymentservice.provider.WebhookResult;
import com.example.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Core domain service shared by {@link
 * com.example.paymentservice.saga.PaymentCommandProcessor} (Kafka-driven)
 * and {@code WebhookController} (HTTP-driven). Both call these methods;
 * each path is thin and delegates here.
 *
 * <p>Invariants enforced here, not at the callers:
 * <ul>
 *   <li>Payment rows are find-or-create keyed on (sagaId, provider), so a
 *       re-sent PaymentCommand never creates a duplicate.</li>
 *   <li>Terminal transitions publish a {@link PaymentReply} on
 *       {@code payment.replies}; non-terminal (INITIATED) does not.</li>
 *   <li>Provider lookup goes through {@link PaymentProviderRegistry} so an
 *       unknown provider name fails fast with a useful error.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String REPLY_BINDING = "paymentReply-out-0";

    private final PaymentRepository payments;
    private final PaymentProviderRegistry providers;
    private final StreamBridge streamBridge;
    private final String defaultProvider;
    private final String currency;

    public PaymentService(PaymentRepository payments,
                          PaymentProviderRegistry providers,
                          StreamBridge streamBridge,
                          @Value("${payment.default-provider:mock}") String defaultProvider,
                          @Value("${payment.currency:INR}") String currency) {
        this.payments = payments;
        this.providers = providers;
        this.streamBridge = streamBridge;
        this.defaultProvider = defaultProvider;
        this.currency = currency;
    }

    /**
     * Entry point for a {@link PaymentCommand}. Resolves the provider,
     * ensures a Payment row exists (idempotent), delegates to the provider,
     * applies the returned state, and publishes a PaymentReply when the
     * outcome is terminal.
     *
     * <p>For hosted-checkout providers that return {@code INITIATED}, no
     * reply is published yet — the webhook will drive the terminal
     * transition via {@link #applyWebhook}.
     */
    @Transactional
    public PaymentOutcome handlePayment(PaymentCommand cmd) {
        String providerName = cmd.provider() == null ? defaultProvider : cmd.provider();
        PaymentProvider provider = providers.require(providerName);

        // Idempotency: find existing (sagaId, provider) row OR create a new one.
        Payment payment = payments.findBySagaIdAndProvider(cmd.sagaId(), providerName)
                .orElseGet(() -> payments.save(
                        new Payment(cmd.sagaId(), cmd.orderId(), cmd.amount(), currency, providerName)));

        // Short-circuit replays — don't invoke the provider twice.
        if (payment.isTerminal()) {
            log.info("Payment already terminal, replaying reply: sagaId={} status={}",
                    cmd.sagaId(), payment.getStatus());
            return publishReplyForTerminal(payment);
        }
        if (payment.getStatus() != Payment.Status.INITIATED) {
            // AUTHORIZED but not yet CAPTURED — stay quiet; the provider will drive us forward.
            log.info("Payment already in flight at provider, no action: sagaId={} status={}",
                    cmd.sagaId(), payment.getStatus());
            return PaymentOutcome.pending(payment);
        }

        // Delegate to the provider.
        ProviderSession session;
        try {
            session = provider.initiate(payment);
        } catch (RuntimeException e) {
            log.error("Provider {} threw during initiate: sagaId={}", providerName, cmd.sagaId(), e);
            payment.markFailed("provider_error: " + e.getClass().getSimpleName());
            payments.save(payment);
            return publishReplyForTerminal(payment);
        }

        return applySessionResult(payment, session);
    }

    /**
     * Entry point for a {@link RefundCommand}. Looks up the Payment the saga
     * is compensating, delegates to the provider for refund, publishes a
     * success/failure PaymentReply.
     */
    @Transactional
    public PaymentOutcome handleRefund(RefundCommand cmd) {
        // Payment to refund — look up by sagaId. We assume one provider per saga.
        // The paymentId from the command is also a safety check.
        Payment payment = payments.findById(cmd.paymentId())
                .orElseThrow(() -> new IllegalStateException(
                        "RefundCommand for unknown paymentId=" + cmd.paymentId() + " sagaId=" + cmd.sagaId()));

        if (payment.getStatus() == Payment.Status.REFUNDED) {
            log.info("Payment already refunded, replaying reply: paymentId={}", cmd.paymentId());
            return publishReplyForTerminal(payment);
        }
        if (payment.getStatus() != Payment.Status.CAPTURED) {
            // Can't refund something we never captured. Reply success=true so the
            // saga doesn't retry forever — the "refund" is a no-op in this case.
            log.warn("Refund requested for non-CAPTURED payment: paymentId={} status={}",
                    cmd.paymentId(), payment.getStatus());
            publishReply(payment, true, "nothing_to_refund:" + payment.getStatus());
            return PaymentOutcome.ok(payment);
        }

        PaymentProvider provider = providers.require(payment.getProvider());
        ProviderSession session;
        try {
            session = provider.refund(payment);
        } catch (RuntimeException e) {
            log.error("Provider {} threw during refund: paymentId={}", payment.getProvider(), cmd.paymentId(), e);
            publishReply(payment, false, "refund_provider_error:" + e.getClass().getSimpleName());
            return PaymentOutcome.failed(payment, "refund_provider_error");
        }

        if (session.status() == Payment.Status.REFUNDED) {
            payment.markRefunded();
            payments.save(payment);
            publishReply(payment, true, "refunded");
        } else {
            payment.markFailed("refund_failed:" + session.failureReason());
            payments.save(payment);
            publishReply(payment, false, session.failureReason());
        }
        return PaymentOutcome.ok(payment);
    }

    /**
     * Called by the webhook controller after a provider notifies us
     * asynchronously. Finds the Payment by provider reference, applies the
     * new state, publishes a PaymentReply if terminal.
     */
    @Transactional
    public Optional<Payment> applyWebhook(String providerName, WebhookResult result) {
        if (result.replayable()) {
            log.info("Webhook replay ignored: provider={} ref={}", providerName, result.providerRef());
            return Optional.empty();
        }

        Optional<Payment> maybe = payments.findByProviderRef(result.providerRef());
        if (maybe.isEmpty()) {
            log.warn("Webhook for unknown providerRef={} provider={}", result.providerRef(), providerName);
            return Optional.empty();
        }
        Payment payment = maybe.get();

        switch (result.newStatus()) {
            case CAPTURED -> {
                if (payment.getStatus() == Payment.Status.CAPTURED) break;      // duplicate webhook
                payment.markCaptured(result.providerRef());
                payments.save(payment);
                publishReply(payment, true, "captured");
            }
            case DECLINED -> {
                if (payment.isTerminal()) break;
                payment.markDeclined(result.failureReason());
                payments.save(payment);
                publishReply(payment, false, result.failureReason());
            }
            case REFUNDED -> {
                if (payment.getStatus() == Payment.Status.REFUNDED) break;
                payment.markRefunded();
                payments.save(payment);
                publishReply(payment, true, "refunded");
            }
            default -> log.warn("Webhook newStatus not actionable: {} ref={}",
                    result.newStatus(), result.providerRef());
        }
        return Optional.of(payment);
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private PaymentOutcome applySessionResult(Payment payment, ProviderSession session) {
        switch (session.status()) {
            case INITIATED -> {
                // Hosted-checkout flow: store the provider's ref, stay INITIATED,
                // wait for webhook. Return the redirect URL so the orchestrator /
                // UI can send the user there.
                payment.linkToProvider(session.providerRef());
                payments.save(payment);
                log.info("Payment INITIATED, waiting for webhook: sagaId={} provider={} ref={}",
                        payment.getSagaId(), payment.getProvider(), session.providerRef());
                return PaymentOutcome.pending(payment, session.redirectUrl());
            }
            case AUTHORIZED -> {
                payment.markAuthorized(session.providerRef());
                payments.save(payment);
                // Not yet terminal; typically providers who do this follow up with CAPTURED.
                return PaymentOutcome.pending(payment);
            }
            case CAPTURED -> {
                payment.markCaptured(session.providerRef());
                payments.save(payment);
                publishReply(payment, true, "captured");
                return PaymentOutcome.ok(payment);
            }
            case DECLINED -> {
                payment.markDeclined(session.failureReason());
                payments.save(payment);
                publishReply(payment, false, session.failureReason());
                return PaymentOutcome.failed(payment, session.failureReason());
            }
            case FAILED -> {
                payment.markFailed(session.failureReason());
                payments.save(payment);
                publishReply(payment, false, session.failureReason());
                return PaymentOutcome.failed(payment, session.failureReason());
            }
            default -> throw new IllegalStateException("Unexpected initiate status: " + session.status());
        }
    }

    private PaymentOutcome publishReplyForTerminal(Payment payment) {
        boolean success = payment.getStatus() == Payment.Status.CAPTURED
                || payment.getStatus() == Payment.Status.REFUNDED;
        publishReply(payment, success, payment.getFailureReason());
        return success ? PaymentOutcome.ok(payment) : PaymentOutcome.failed(payment, payment.getFailureReason());
    }

    private void publishReply(Payment payment, boolean success, String failureReason) {
        PaymentReply reply = new PaymentReply(
                payment.getSagaId(),
                payment.getOrderId(),
                success,
                success ? null : failureReason,
                payment.getId());
        streamBridge.send(REPLY_BINDING, reply);
        log.info("PaymentReply sent: sagaId={} success={} paymentId={} reason={}",
                payment.getSagaId(), success, payment.getId(), failureReason);
    }
}
