package io.matrix.contract.bridge;

import io.matrix.contract.Outcome;

/**
 * W51 — where an accepted bridged message has its effect.
 *
 * <p><b>The sink is the effect, and it is the only thing that writes.</b> Everything before
 * it — the signature, the capability, the budget, the idempotency, the audit — decides
 * whether the message may have an effect at all. The sink is what the message does.</p>
 *
 * <p><b>W52: the sink may also answer.</b> The first direction of the bridge is a delivery:
 * chain B offers something that happened, and chain A records it. The second direction
 * carries established knowledge, and the receiver's brain does something with it — takes it
 * into its memory, or already has it. That "what the receiver did with it" is the
 * {@link Outcome} the sink returns, and it is what travels back over the wire.</p>
 *
 * <p><b>A null outcome is not a failure.</b> A sink that has nothing to report — the
 * delivery direction, for instance — returns null, and the answer carries the decision
 * alone. The decision says whether the message was accepted; the outcome says what the
 * receiver did with it, and only a receiver that did something has one.</p>
 */
@FunctionalInterface
public interface BridgeSink {

    /**
     * The effect of an accepted message, and what the receiver did with it.
     *
     * @param envelope the accepted message
     * @return the outcome, or null when the receiver has nothing to report
     */
    Outcome accept(BridgeEnvelope envelope);
}
