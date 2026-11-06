package io.matrix.knowledge;

/**
 * The kind of a source, which determines how its evidence may be weighted
 * (owner directive §15): authority, freshness and experimental quality are
 * different dimensions and are not interchangeable.
 */
public enum SourceKind {
    /** An authoritative reference: a textbook, a standard, a specification. */
    AUTHORITATIVE_REFERENCE,
    /** A peer-reviewed or otherwise reviewed publication. */
    PUBLICATION,
    /** A primary observation or measurement. */
    OBSERVATION,
    /** An experiment with a recorded method and result. */
    EXPERIMENT,
    /** A dataset with a stated provenance. */
    DATASET,
    /** A secondary report of another source. */
    SECONDARY_REPORT,
    /** A derivation from accepted mathematics. */
    MATHEMATICAL_DERIVATION,
    /** An explicitly identified human statement. */
    HUMAN_ASSERTION,
    /** A hypothesis produced by a model, not by evidence. */
    MODEL_GENERATED
}
