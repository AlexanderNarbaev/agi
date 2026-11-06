package io.matrix.cognitive.microworld;

/**
 * W44.4 — a hypothesis the session has ruled out, and why.
 *
 * <p>The record exists because a rejected hypothesis is EVIDENCE, not rubbish. A learner that
 * silently drops the candidates its observations rule out cannot show what it considered, cannot
 * explain why the survivors are the survivors, and cannot notice when a rule it once held comes
 * back into play. The rejection is therefore kept, with the observation that caused it.</p>
 *
 * <p><b>Nothing is ever deleted.</b> The session keeps every rejected hypothesis for the whole
 * episode, and the reason names the observation that first contradicted it. The counts are the
 * ones the hypothesis had at the moment it was rejected — they are a historical fact, and
 * recomputing them later would rewrite the record of what was known when.</p>
 *
 * @param hypothesis the ruled-out hypothesis, with its counts at the moment of rejection
 * @param reason     why it was ruled out, naming the observation that contradicted it
 */
public record RejectedHypothesis(RuleHypothesis hypothesis, String reason) {

    public RejectedHypothesis {
        if (hypothesis == null) {
            throw new IllegalArgumentException("a rejection must name the hypothesis it rejects");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a rejection must say why");
        }
    }

    /** The identifier of the rejected hypothesis. */
    public String hypothesisId() {
        return hypothesis.id();
    }

    @Override
    public String toString() {
        return "RejectedHypothesis[" + hypothesis.id() + ": " + reason + "]";
    }
}
