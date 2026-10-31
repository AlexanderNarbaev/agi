package io.matrix.knowledge;

/**
 * The abstraction level of a representation.
 *
 * <p>Progressive Epistemic Refinement (owner directive §7): the same concept
 * is represented at several levels of detail, and each level RETAINS its own
 * scope and assumptions. A simpler model is not automatically false and a
 * more elaborate one is not automatically correct — what matters is that the
 * relationship between them is recorded.
 *
 * <p>The order is a refinement order, not a quality order: {@link #INTRODUCTORY}
 * is not "worse" than {@link #MECHANISTIC}, it is for a different audience and
 * a different task.
 */
public enum AbstractionLevel {

    /** A first explanation for a reader with no prior knowledge of the domain. */
    INTRODUCTORY,

    /** Adds the structure a practitioner needs to reason about the subject. */
    INTERMEDIATE,

    /** Adds mechanisms, dependencies and the quantitative relationships. */
    ADVANCED,

    /** The formal or mechanistic account, with its equations and constraints. */
    MECHANISTIC,

    /** The procedure: what to do, in what order, with which expected result. */
    OPERATIONAL,

    /** A legitimate alternative account, kept alongside the accepted one. */
    ALTERNATIVE,

    /** A hypothesis that has not been established. */
    HYPOTHESIS;

    /** True when this level refines {@code other}. */
    public boolean refines(AbstractionLevel other) {
        return ordinal() > other.ordinal() && other != ALTERNATIVE && this != ALTERNATIVE;
    }
}
