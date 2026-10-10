package com.example.common.saga;

import java.util.UUID;

/**
 * Marker interface for every message sent FROM the saga orchestrator TO a
 * downstream service. Each command carries at least {@link #sagaId()} so the
 * target service can route its reply back to the right saga instance.
 *
 * <h3>Why sealed</h3>
 * Sealed interfaces turn "every saga command has sagaId" from a convention
 * into a <em>compiler-enforced</em> contract. The {@code permits} clause is
 * also a <em>static catalogue</em> of every command in the system — grep
 * this one file to see the full set, and the compiler will refuse to
 * compile a new command record until it's added to the permits list.
 *
 * <h3>Why not an abstract class</h3>
 * Java records cannot extend abstract classes (they implicitly extend
 * {@code java.lang.Record}). We like records here — 3 lines of declaration,
 * free equals/hashCode/toString, Jackson-native serialization, immutable
 * by default. The marker interface gives us the shared contract without
 * giving up records.
 *
 * <h3>What this is NOT</h3>
 * This is not a polymorphic dispatch point. Each command still has its own
 * Kafka topic and its own typed consumer. No code calls
 * {@code handle(SagaCommand)}. The interface exists for the compile-time
 * contract + discoverability, not runtime polymorphism.
 */
public sealed interface SagaCommand permits
        PaymentCommand,
        RefundCommand,
        NotifyUserCommand,
        AuthorizePaymentCommand,
        CapturePaymentCommand,
        VoidPaymentCommand {

    UUID sagaId();
}
