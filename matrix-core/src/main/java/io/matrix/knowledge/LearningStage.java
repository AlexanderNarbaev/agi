package io.matrix.knowledge;

/**
 * The stages of the learning progression (owner directive §9).
 *
 * <p>These describe the CURRENT CAPABILITY regarding one concept and one
 * dimension. They do not describe the maturity of the system, and they are
 * reached by evidence, not by declaration.
 */
public enum LearningStage {

    /** No sufficient reliable representation or demonstrated ability. */
    UNKNOWN(0),

    /** Investigating: the subject is open and the evidence is thin. */
    EXPLORATION(1),

    /** A first usable model exists and has been checked for basic recall. */
    INITIAL_LEARNING(2),

    /** The capability is being applied to real tasks, with mixed results. */
    PRACTICE(3),

    /** Predefined acceptance criteria are met, repeatedly and on variants. */
    COMPETENCE(4),

    /** The principles can be explained and transferred, not just recalled. */
    UNDERSTANDING(5),

    /** The capability is established well enough to teach to another learner. */
    KNOWLEDGE(6);

    private final int index;

    LearningStage(int index) {
        this.index = index;
    }

    /** The ordinal of the stage, from 0 at UNKNOWN. */
    public int index() {
        return index;
    }

    /** The stage at an ordinal, clamped to the known range. */
    public static LearningStage of(int index) {
        LearningStage[] values = values();
        if (index < 0) {
            return UNKNOWN;
        }
        if (index >= values.length) {
            return KNOWLEDGE;
        }
        return values[index];
    }

    /** True when this stage is at least as advanced as the other. */
    public boolean atLeast(LearningStage other) {
        return index >= other.index;
    }
}
