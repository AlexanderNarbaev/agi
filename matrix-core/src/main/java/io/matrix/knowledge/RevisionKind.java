package io.matrix.knowledge;

/**
 * The kind of change a knowledge revision records (owner directive §13).
 *
 * <p>The distinction matters because a revision that adds resolution is not a
 * correction, and a correction is not a refutation of the earlier record.
 */
public enum RevisionKind {
    /** A later, more detailed representation of the same concept. */
    REFINEMENT,
    /** A new representation at a different abstraction level. */
    DIFFERENT_ABSTRACTION,
    /** A correction: the earlier representation was wrong. */
    CORRECTION,
    /** A different scope or boundary condition. */
    SCOPE_CHANGE,
    /** Sources disagree and both accounts are kept. */
    CONFLICTING_SCHOOLS,
    /** The evidence was insufficient to decide. */
    INCOMPLETE_EVIDENCE,
    /** The measurement was uncertain. */
    MEASUREMENT_UNCERTAINTY,
    /** The claim was withdrawn. */
    SUPERSESSION
}
