package io.matrix.contract.bridge;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * W51 — the in-memory idempotency registry (BRIDGE-8).
 *
 * <p><b>How the promise is kept.</b> The registry is a {@link ConcurrentHashMap} and the
 * single operation is {@link ConcurrentMap#computeIfAbsent(Object, java.util.function.Function)}.
 * That primitive computes the value for a key at most once even when several threads race on
 * it: the losers wait and then read the winner's value. The effect — the write to the
 * epistemic store — therefore happens once, and every caller receives the same decision.</p>
 *
 * <p><b>What it does not do.</b> It does not survive a restart. BRIDGE-8 requires the
 * idempotency to be durable; this implementation is the in-memory one the contract names as
 * the development shape, and the durable one is the server side's business. A bridge whose
 * idempotency is only in memory will duplicate an observation if it is restarted between the
 * write and the retry — which is exactly the case the durable implementation exists for, and
 * the reason the interface is here rather than the class.</p>
 *
 * <p><b>One constraint on the supplier.</b> The supplier runs while the map holds the key, so
 * it must not call back into this registry: that would be a re-entrant modification of the
 * map, and the JDK's concurrent map detects it and throws. The receiver's supplier does not
 * — it writes to the store, writes to the audit log and builds a decision.</p>
 */
public final class InMemoryBridgeIdempotency implements BridgeIdempotency {

    private final ConcurrentMap<String, Outcome> outcomes = new ConcurrentHashMap<>();

    @Override
    public Optional<Outcome> find(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(outcomes.get(requestId));
    }

    @Override
    public Outcome firstOutcome(String requestId, String payloadDigest,
                                 Supplier<Outcome> firstDelivery) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("an idempotency key must not be blank");
        }
        if (payloadDigest == null || payloadDigest.isBlank()) {
            throw new IllegalArgumentException("an idempotency key needs the digest of the payload");
        }
        if (firstDelivery == null) {
            throw new IllegalArgumentException("there is no delivery to record");
        }
        return outcomes.computeIfAbsent(requestId, key -> firstDelivery.get());
    }

    @Override
    public int recordedCount() {
        return outcomes.size();
    }
}
