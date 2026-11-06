package io.matrix.knowledge;

/** What an evidence record says about a claim (owner directive §15). */
public enum EvidenceRelation {
    /** The source supports the claim. */
    SUPPORTS,
    /** The source contradicts the claim. */
    CONTRADICTS,
    /** The source is about the subject but does not bear on the claim. */
    CONTEXTUAL,
    /**
     * RECON-W43.0. The source is an EXAMPLE of the claim: a situation in which
     * the claim holds. One example is not a universal rule — the promotion
     * policy, not this relation, is what holds that line.
     */
    EXEMPLIFIES,
    /**
     * RECON-W43.0. The source narrows the claim: it is true, but not in the
     * full generality the claim states. A penguin qualifies "all birds fly"
     * without contradicting "most birds fly under ordinary conditions".
     */
    QUALIFIES,
    /**
     * RECON-W43.0. The source was found, read, and does NOT support the
     * claim. This is NOT the same as CONTRADICTS: the source is about the
     * subject and says nothing that bears the claim's way.
     */
    FAILS_TO_SUPPORT,
    /**
     * RECON-W43.0. The evidence does not decide the claim. This is the
     * relation that keeps the absence of evidence from being read as the
     * evidence of absence.
     */
    INSUFFICIENT
}
