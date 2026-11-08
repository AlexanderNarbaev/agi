package io.matrix.contract.bridge;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * W51 — the registry that makes the bridge idempotent per request id (BRIDGE-8).
 *
 * <p><b>What BRIDGE-8 asks for.</b> "The bridge is idempotent per request id: a repeated
 * delivery of the same request id does not duplicate the observation, and the effect is
 * recorded once. A caller that retries after a timeout gets the outcome of the first attempt,
 * not a second observation." Two separate promises: the effect happens once, and the retry
 * gets the <i>first</i> answer rather than a new one.</p>
 *
 * <p><b>Why the interface has one method and not two.</b> A registry with a
 * {@code find}-then-{@code remember} pair cannot keep the promise under concurrency: two
 * deliveries of the same request id can both find nothing and both proceed, and the
 * observation is written twice. The single method {@link #firstOutcome(String, String,
 * Supplier)} takes the work as a supplier and runs it <b>at most once</b> for a request id,
 * so the check and the act cannot be separated. That is the whole point of the interface, and
 * it is why the work is passed in rather than returned.</p>
 *
 * <p><b>Why the digest is part of the registry's state.</b> A request id that arrives twice
 * with two different payloads is not a retry, it is a conflict, and the two must not be
 * confused: a retry must return the first outcome, while a conflict must be refused
 * loudly. Storing the digest alongside the outcome is what lets the reader tell them apart.
 * {@link io.matrix.contract.Correlation} says the same thing in its own words: "two different
 * commands may not share an idempotency key: the second one is a conflict, not a retry".</p>
 *
 * <p><b>What this interface does not provide.</b> BRIDGE-8 also says the idempotency is
 * "durable, so it survives the death of either process". The implementation in this package
 * is in memory and does not survive a restart; durability is the server side's business, and
 * this interface is the seam where that implementation will be substituted.</p>
 */
public interface BridgeIdempotency {

    /**
     * The outcome of a request id, with the digest of the payload that produced it.
     *
     * @param decision     what was decided
     * @param payloadDigest the digest of the payload the decision was made about
     */
    record Outcome(BridgeDecision decision, String payloadDigest) {

        public Outcome {
            if (decision == null) {
                throw new IllegalArgumentException("an outcome needs a decision");
            }
            if (payloadDigest == null || payloadDigest.isBlank()) {
                throw new IllegalArgumentException("an outcome needs the digest of the payload");
            }
        }
    }

    /**
     * The outcome already recorded for a request id, if there is one.
     *
     * @param requestId the request id
     * @return the recorded outcome, or empty when the request id is new
     */
    Optional<Outcome> find(String requestId);

    /**
     * The outcome of a request id, computed once.
     *
     * <p>When the request id is new, the supplier is run and its result is recorded; when it
     * is already known, the supplier is not run and the recorded outcome is returned. The
     * supplier must therefore be free of side effects that must not happen twice — it is the
     * place where the bridge writes the effect, and it runs at most once.</p>
     *
     * @param requestId     the request id, which is the idempotency key
     * @param payloadDigest the digest of the payload the call carries
     * @param firstDelivery the work to do the first time this request id is seen
     * @return the outcome, whether it was computed now or recorded earlier
     */
    Outcome firstOutcome(String requestId, String payloadDigest, Supplier<Outcome> firstDelivery);

    /**
     * The number of request ids this registry has recorded.
     *
     * @return the count
     */
    int recordedCount();
}
