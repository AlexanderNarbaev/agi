package io.matrix.knowledge;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — one piece of evidence a task requires.
 *
 * <p><b>What this is.</b> A requirement is a claim the task <b>cannot be done without</b>. It
 * is not a claim the reading happened to reach, and it is not a claim that is merely relevant.
 * The distinction matters because the previous milestone measured the cost of confusing them:
 * a scope that held the claim the reading cited <i>first</i> was admitted, and the answer it
 * served was missing a claim the answer needed.</p>
 *
 * <p><b>What it carries, and what it does not judge.</b> The record carries three facts that
 * belong to the evidence — the claim's identifier, the proposition it states, the source it
 * came from and the claim's epistemic standing — and it keeps them <b>apart</b>:</p>
 *
 * <ul>
 *   <li><b>relevance</b> is <b>not</b> here. How well a claim matches the question is the
 *       retriever's number, and a prerequisite is not a relevance score: a claim can be
 *       irrelevant-looking and still be the thing the task cannot be done without.</li>
 *   <li><b>confidence</b> is <b>not</b> here. The claim's authored confidence is a number the
 *       import path produced; it says how sure the source was, not whether the task needs the
 *       claim, and a requirement is never admitted or refused because of it.</li>
 *   <li><b>provenance</b> is carried, not judged. The source a requirement came from is the
 *       thing that decides whether a scope <i>carries</i> it, and it never decides whether the
 *       claim is <i>required</i>.</li>
 *   <li><b>applicability</b> is a different question entirely: whether a competence may serve a
 *       task at all is the route's decision, and this record does not make it.</li>
 * </ul>
 *
 * @param claimId     the claim's identifier
 * @param proposition the proposition the claim states
 * @param sourceId    the source the claim was introduced by — the provenance, carried
 * @param status      the claim's epistemic standing — carried, never judged here
 * @param reason      why this claim is required, in words a reader can act on
 */
public record EvidenceRequirement(
        String claimId,
        String proposition,
        String sourceId,
        ClaimStatus status,
        String reason) {

    public EvidenceRequirement {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("a requirement must name the claim it requires");
        }
        if (proposition == null || proposition.isBlank()) {
            throw new IllegalArgumentException("a requirement must carry the proposition the "
                    + "claim states");
        }
        reason = reason == null ? "" : reason;
    }
}
