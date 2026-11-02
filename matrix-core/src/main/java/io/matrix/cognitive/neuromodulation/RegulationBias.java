package io.matrix.cognitive.neuromodulation;

/**
 * W44.3 — the bounded influence a regulatory state is allowed to have on one decision.
 *
 * <p>This is the mechanism that keeps neuromodulation from becoming the thing the W44 directive
 * forbids. A drive does not reach into a subsystem; it produces a bias, the subsystem asks for
 * the bias it is allowed to use, and the bias is a number in a fixed range.</p>
 *
 * <p>Every field is bounded and every default is the NEUTRAL value: a multiplier of 1.0, a
 * threshold of 1.0 (meaning "never demand more evidence than the default") and admission of
 * non-critical tasks. A system with no regulatory state therefore behaves exactly as a system
 * with neutral regulation, which is what makes the effect of a drive measurable by comparing
 * two runs that differ in one drive.</p>
 *
 * <p>There is deliberately no field for confidence, truth, permission or safety. A drive cannot
 * make a claim more established and cannot authorise anything.</p>
 */
public record RegulationBias(
        double costWeightMultiplier,
        double evidenceRequestThreshold,
        boolean admitNonCriticalTask,
        double taskPriorityMultiplier) {

    /** The cost multiplier that changes nothing. */
    public static final double NEUTRAL_MULTIPLIER = 1.0;

    /** The evidence-request threshold that never triggers. */
    public static final double NEVER_REQUEST_EVIDENCE = 1.0;

    /** The bias a system with no regulatory state uses. */
    public static RegulationBias neutral() {
        return new RegulationBias(NEUTRAL_MULTIPLIER, NEVER_REQUEST_EVIDENCE, true, NEUTRAL_MULTIPLIER);
    }

    public RegulationBias {
        if (costWeightMultiplier < 0.0 || Double.isNaN(costWeightMultiplier)) {
            throw new IllegalArgumentException("cost weight multiplier must not be negative");
        }
        if (evidenceRequestThreshold < 0.0 || evidenceRequestThreshold > 1.0
                || Double.isNaN(evidenceRequestThreshold)) {
            throw new IllegalArgumentException("evidence request threshold must be within [0,1]");
        }
        if (taskPriorityMultiplier < 0.0 || Double.isNaN(taskPriorityMultiplier)) {
            throw new IllegalArgumentException("task priority multiplier must not be negative");
        }
    }
}
