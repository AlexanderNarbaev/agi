package io.matrix.contract.bridge;

import io.matrix.contract.Outcome;

/**
 * W52 — the answer to a bridged message: the decision, and what the receiver did with it.
 *
 * <p><b>Why the two are together.</b> The decision says whether the message was accepted
 * under the protocol — the signature verified, the right granted, the budget allowed, the
 * request id fresh. The outcome says what happened next: in the first direction of the
 * bridge there is nothing to report, and in the second one the receiver took the offered
 * knowledge into its own memory, or already had it. A sender that got only the decision
 * would have to guess the second half, and a guess is not a protocol.</p>
 *
 * <p><b>A null outcome is a fact, not a hole.</b> It means the receiver had nothing to
 * report — the delivery direction, or a receiver that decided to do nothing with what it
 * was offered. It is not a failure, and the decision alone says whether the message was
 * accepted.</p>
 *
 * @param decision the decision the protocol produced
 * @param outcome  what the receiver did with the message, or null when it had nothing to report
 */
public record BridgeAnswer(BridgeDecision decision, Outcome outcome) {

    public BridgeAnswer {
        if (decision == null) {
            throw new IllegalArgumentException("an answer needs a decision");
        }
    }

    /**
     * An answer that carries the decision alone.
     *
     * @param decision the decision
     * @return the answer
     */
    public static BridgeAnswer of(BridgeDecision decision) {
        return new BridgeAnswer(decision, null);
    }

    /**
     * True when the message was accepted.
     *
     * @return true when the decision is an acceptance
     */
    public boolean accepted() {
        return decision.status() == BridgeDecision.Status.ACCEPTED;
    }
}
