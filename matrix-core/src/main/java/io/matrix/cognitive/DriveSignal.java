package io.matrix.cognitive;

/**
 * W44.3 — a typed internal regulatory signal.
 *
 * <p>The W44 directive's core rule about neuromodulation is that a biological name must not be a
 * magic number, and that a regulator must not be permitted to change a decision merely because its
 * name suggests that it should. This record is the answer to both: a drive is a NAMED DIMENSION
 * with a bounded level, a declared source and a declared effect. The effect is what the dimension
 * is allowed to influence — and nothing outside that list.</p>
 *
 * <p>The dimensions are computational, not biochemical. {@code UNCERTAINTY} means "the internal
 * model does not discriminate between the live hypotheses"; it is not cortisol, and
 * {@code SUSTAINED_LOAD} is not a hormone level. Where a biological analogy is used, it is used as
 * a functional label for a control role, never as a claim about concentrations.</p>
 *
 * <p>{@code effect} names the ONE downstream knob this drive is allowed to bias. A drive whose
 * effect is not in the allowed set cannot be constructed. This is what makes "a modulator changed
 * a decision" an auditable statement rather than a hope: the trace can say which drive, which
 * effect, and what the value was before and after.</p>
 */
public record DriveSignal(
        Dimension dimension,
        double level,
        String source,
        Effect effect,
        String updatedAt) {

    /** The bounded set of regulatory dimensions MATRIX actually uses. */
    public enum Dimension {
        /** How sharply the system filters what it attends to. */
        ATTENTION,
        /** How poorly the current model discriminates between live hypotheses. */
        UNCERTAINTY,
        /** The difference between the expected and the achieved outcome of a task. */
        REWARD_PREDICTION_ERROR,
        /** How much the current input differs from what the model has seen. */
        NOVELTY,
        /** Accumulated operational pressure that has not been relieved. */
        SUSTAINED_LOAD,
        /** Readiness to react to an unexpected event. */
        VIGILANCE,
        /** The push to resolve an identified knowledge gap. */
        CURIOSITY,
        /** The need to consolidate what was learned. */
        CONSOLIDATION_DEMAND
    }

    /**
     * The single downstream knob a drive may bias.
     *
     * <p>These are the ONLY effects a drive is allowed to have in W44. There is deliberately no
     * "confidence", no "truth", no "permission" effect: a regulator must never be able to make a
     * claim more established, and it must never be able to authorise anything (W44 §11.4).</p>
     */
    public enum Effect {
        /** Biases which algorithm the selector prefers, within measured preconditions. */
        ALGORITHM_COST_PREFERENCE,
        /** Biases whether the system asks for more evidence before answering. */
        EVIDENCE_REQUEST,
        /** Biases whether the system proposes a bounded internal task. */
        TASK_ADMISSION,
        /** Biases the priority of a proposed internal task. */
        TASK_PRIORITY,
        /** No downstream effect at all — the drive is recorded only. */
        NONE
    }

    public DriveSignal {
        if (dimension == null) {
            throw new IllegalArgumentException("drive dimension must not be null");
        }
        if (effect == null) {
            throw new IllegalArgumentException("drive effect must not be null");
        }
        if (level < 0.0 || level > 1.0 || Double.isNaN(level)) {
            throw new IllegalArgumentException("drive level must be within [0,1]");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("drive source must not be blank");
        }
    }

    /** A drive with no effect: recorded, but not allowed to change anything. */
    public static DriveSignal inert(Dimension dimension, double level, String source, String updatedAt) {
        return new DriveSignal(dimension, level, source, Effect.NONE, updatedAt);
    }

    /** The same dimension at a new level, produced by a different source. */
    public DriveSignal withLevel(double newLevel, String newSource, String timestamp) {
        return new DriveSignal(dimension, newLevel, newSource, effect, timestamp);
    }

    @Override
    public String toString() {
        return "DriveSignal[" + dimension + "=" + level + " effect=" + effect + " source=" + source + "]";
    }
}
