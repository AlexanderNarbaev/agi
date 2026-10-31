package io.matrix.knowledge;

/**
 * The recorded result of one assessment (owner directive §28, §31).
 *
 * <p>The learner's claimed confidence is recorded alongside the outcome,
 * because a capability that is not calibrated is not a capability: a learner
 * who is right by luck and a learner who is right by knowledge must be
 * distinguishable, and only the confidence reveals which one it was.
 */
public record AssessmentOutcome(
        String itemId,
        boolean correct,
        double score,
        double claimedConfidence,
        String learnerAnswer,
        String sessionId,
        long answeredAt) {

    public AssessmentOutcome {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("outcome item id must not be blank");
        }
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("outcome score must be within [0,1]");
        }
    }
}
