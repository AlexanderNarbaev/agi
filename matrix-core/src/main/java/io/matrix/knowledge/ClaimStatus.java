package io.matrix.knowledge;

/**
 * The epistemic status of a claim (owner directive §15).
 *
 * <p>The semantics are deliberately narrow:
 *
 * <ul>
 *   <li>{@link #DRAFT} — asserted by the system itself and not yet checked
 *       against any source. A draft with no source is a HYPOTHESIS: the
 *       system's own proposal, carrying no external support.</li>
 *   <li>{@link #CANDIDATE} — extracted from a source and recorded with its
 *       provenance, but not yet validated by any procedure. The mission's
 *       rule: "Extracted information is provisional until validated by
 *       appropriate procedures." A candidate may carry evidence; that
 *       evidence shows where the claim came from, not that it is
 *       established.</li>
 *   <li>{@link #SUPPORTED} — at least one source supports it; it has not been
 *       verified against an authoritative or experimental check.</li>
 *   <li>{@link #VERIFIED} — the supporting evidence meets the acceptance
 *       criteria for this domain. The evidence link records WHICH source.</li>
 *   <li>{@link #CONTESTED} — credible sources disagree. The claim is neither
 *       true nor false; the disagreement is preserved.</li>
 *   <li>{@link #SUPERSEDED} — it was once the accepted account and a later
 *       revision replaced it. The record is kept.</li>
 *   <li>{@link #REFUTED} — the evidence contradicts it.</li>
 *   <li>{@link #UNKNOWN} — the evidence is insufficient to decide.</li>
 * </ul>
 *
 * <p>A source being available does NOT promote a claim: that requires an
 * evidence link with a relation of SUPPORTS.
 */
public enum ClaimStatus {
    DRAFT,
    CANDIDATE,
    SUPPORTED,
    VERIFIED,
    CONTESTED,
    SUPERSEDED,
    REFUTED,
    UNKNOWN,
    /**
     * RECON-W43.0. The system SAW this: it is on record as an observation,
     * and it is not yet a claim anyone has argued for.
     */
    OBSERVED,
    /**
     * RECON-W43.0. An assertion is on record and nothing supports it yet.
     * A user's correction lands here first — it is evidence about what the
     * user meant, and at most a candidate claim.
     */
    HYPOTHESIS,
    /**
     * RECON-W43.0. The evidence was sought and does not decide. This is not
     * the same as REFUTED, and it is not the same as UNKNOWN: something was
     * looked at, and it did not settle the matter.
     */
    INSUFFICIENT_EVIDENCE,
    /**
     * RECON-W43.0. The claim was replaced by a narrower one, and BOTH are
     * kept. The status says what happened; the earlier claim remains
     * readable, and the revision that links them remains readable.
     */
    REFINED
}
