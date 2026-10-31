package io.matrix.knowledge;

/** What an evidence record says about a claim (owner directive §15). */
public enum EvidenceRelation {
    /** The source supports the claim. */
    SUPPORTS,
    /** The source contradicts the claim. */
    CONTRADICTS,
    /** The source is about the subject but does not bear on the claim. */
    CONTEXTUAL
}
