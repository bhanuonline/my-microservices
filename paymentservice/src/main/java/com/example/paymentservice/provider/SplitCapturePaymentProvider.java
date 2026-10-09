package com.example.paymentservice.provider;

import com.example.paymentservice.model.Payment;

/**
 * Marker + method set for providers that support an explicit two-phase
 * (authorize → capture) payment lifecycle plus unused-authorization void.
 *
 * <p>Combined-flow providers (Mock, Stripe Checkout, Razorpay, PayPal CAPTURE
 * intent) do <b>not</b> implement this interface — their {@link
 * PaymentProvider#initiate initiate()} already does authorize + capture
 * atomically from the saga's perspective. Only {@code CheckoutDotComPayment
 * Provider} (Phase 5 Commit 2) implements this.
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   1. authorize(payment)  → provider reserves funds; Payment=AUTHORIZED
 *   2a. capture(payment)   → provider moves funds;    Payment=CAPTURED
 *   2b. OR void(payment)   → release reservation;     Payment=VOIDED (free)
 *   3. refund(payment)     → reverse a CAPTURED payment (inherited)
 * </pre>
 *
 * <h3>Why a sub-interface instead of default methods</h3>
 * The orchestrator picks a path at saga-start based on whether the
 * provider is split-capable:
 *
 * <pre>
 *   if (provider instanceof SplitCapturePaymentProvider) {
 *       // send AuthorizePaymentCommand
 *   } else {
 *       // send PaymentCommand (combined flow)
 *   }
 * </pre>
 *
 * {@code instanceof} is explicit and static-analysis-friendly. Default
 * methods throwing {@link UnsupportedOperationException} would push the
 * capability check to runtime at the call site, which is uglier.
 *
 * <h3>Interaction with {@code initiate()}</h3>
 * Implementations SHOULD still implement {@link PaymentProvider#initiate} —
 * typically it delegates to {@code authorize() + capture()} internally, so
 * a caller sending the old combined {@link
 * com.example.common.saga.PaymentCommand} still gets a working flow against
 * a split-capable provider.
 */
public interface SplitCapturePaymentProvider extends PaymentProvider {

    /**
     * Reserve funds on the customer's instrument without moving them.
     * Returns a {@link ProviderSession} with:
     * <ul>
     *   <li>{@code status=AUTHORIZED} on success + the provider's
     *       authorization reference as {@code providerRef}</li>
     *   <li>{@code status=DECLINED} if the provider rejected (card declined,
     *       fraud, etc.) + a {@code failureReason}</li>
     *   <li>{@code status=FAILED} on system error</li>
     * </ul>
     */
    ProviderSession authorize(Payment payment);

    /**
     * Convert a prior authorization into an actual charge.
     * Precondition: {@code payment.getStatus() == AUTHORIZED} and
     * {@code payment.getProviderRef()} is populated.
     *
     * <p>Returns {@link ProviderSession} with:
     * <ul>
     *   <li>{@code status=CAPTURED} on success</li>
     *   <li>{@code status=FAILED} on error — Payment stays AUTHORIZED
     *       at the caller; operator can retry or void</li>
     * </ul>
     */
    ProviderSession capture(Payment payment);

    /**
     * Release a prior authorization without charging. Typically free — no
     * money ever moved. Precondition: {@code payment.getStatus() ==
     * AUTHORIZED}.
     *
     * <p>Returns {@link ProviderSession} with:
     * <ul>
     *   <li>{@code status=VOIDED} on success (new terminal state)</li>
     *   <li>{@code status=FAILED} on error (rare — commonly means the
     *       auth already expired at the provider, which is effectively
     *       the same end state and could be treated as success)</li>
     * </ul>
     */
    ProviderSession voidPayment(Payment payment);
}
