package io.matrix.knowledge;

/**
 * The acceptance criteria for a learning-state transition
 * (owner directive §31).
 *
 * <p>They are configurable and recorded with every transition, because
 * "competent" is not a property of a learner - it is a property of a
 * learner measured against a stated bar. The thresholds below are a
 * starting policy, not a universal law: the owner directive is explicit
 * that one threshold must not be applied to every capability, and the
 * evaluator therefore takes the criteria per dimension.
 *
 * <p>The starting policy requires at least two independent items across at
 * least two variants, including a held-out item, a mean score at or above
 * the competence threshold, and no critical failure.
 */
public record MasteryCriteria(
        int minimumItems,
        int minimumVariants,
        int minimumHeldOutItems,
        double competenceThreshold,
        double understandingThreshold,
        boolean requireNoCriticalFailure) {

    /** The default policy. */
    public static MasteryCriteria defaults() {
        return new MasteryCriteria(2, 2, 1, 0.8, 0.8, true);
    }

    /** A laxer policy for the early stages, where the point is to try. */
    public static MasteryCriteria exploratory() {
        return new MasteryCriteria(1, 1, 0, 0.5, 0.7, false);
    }
}
