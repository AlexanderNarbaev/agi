package io.matrix.contract.bridge;

import io.matrix.contract.Provenance;

import java.util.List;

/**
 * W53 — the secondary report a remote cognitive process returns.
 *
 * <p><b>Why this record exists at all.</b> The W53 mandate says the remote result must
 * come back as a report that the epistemic model can evaluate, and the research track on
 * cooperative reasoning found the concrete reason the existing bridge cannot deliver
 * one: the sink writes the evidence relation as {@code SUPPORTS} unconditionally, so a
 * remote report that <i>denies</i> an established belief can only ever arrive as support
 * for a new candidate and never reaches the refutation path. This record carries the
 * relation explicitly, so the receiver can write a contradiction when the remote process
 * produced one.</p>
 *
 * <p><b>Why it carries its lineage.</b> The same research found that the derivation chain
 * is dropped at the sink boundary: the canonical {@link Provenance} carries
 * {@code derivedFrom}, but the workbench's own records have no lineage field, so the
 * circular-evidence check had nothing to run on. The report therefore names the records
 * it rests on. A proposition that Chain A supplied to Chain B and that returns as a report
 * is then <b>recognisable as returning</b> — it is not independent corroboration, and the
 * receiver can say so.</p>
 *
 * <p><b>What it is not.</b> It is not a belief, not an established fact, and not a
 * shortcut around the epistemic evaluation. It is a statement <i>by the peer</i>, with
 * the peer's own uncertainty and the peer's own limits, and the receiver decides what it
 * is worth.</p>
 *
 * @param protocolVersion        the bridge protocol the report was written in
 * @param taskId                 the task the report answers
 * @param requestId              the request the report answers
 * @param subjectProposition     the proposition the report is about
 * @param subjectClaimId         the claim it is about, when the sender knows it
 * @param relation               whether the report supports or contradicts it
 * @param confidence             the peer's confidence, if it stated one
 * @param confidenceBasis        what that confidence rests on
 * @param derivedFrom            the sender-side records the report rests on
 * @param responderId            the process that produced the report
 * @param capabilityId           the capability that produced it
 * @param capabilityVersion      the version of that capability
 * @param limitations            what the report does not cover
 * @param provenance             where the report came from
 * @param at                     when the report was produced
 */
public record CognitiveTaskReport(
        int protocolVersion,
        String taskId,
        String requestId,
        String subjectProposition,
        String subjectClaimId,
        Relation relation,
        Double confidence,
        ConfidenceBasis confidenceBasis,
        List<String> derivedFrom,
        String responderId,
        String capabilityId,
        String capabilityVersion,
        List<String> limitations,
        Provenance provenance,
        String at) {

    /** The relation a report bears to the proposition it is about. */
    public enum Relation {
        /** The report supports the proposition. */
        SUPPORTS,
        /** The report contradicts the proposition. */
        CONTRADICTS,
        /** The report neither supports nor contradicts: it says something else. */
        INDEPENDENT
    }

    /** What a confidence rests on. */
    public enum ConfidenceBasis {
        /** The peer declared it. */
        DECLARED,
        /** The peer measured it. */
        MEASURED,
        /** Independent evidence established it. */
        INDEPENDENT,
        /** Nobody knows. */
        UNKNOWN
    }

    /** The report carries no lineage field: the constructor rejects it. */
    public CognitiveTaskReport {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("the report must name its task");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("the report must name its request");
        }
        if (subjectProposition == null || subjectProposition.isBlank()) {
            throw new IllegalArgumentException("the report must name the proposition it is about");
        }
        if (relation == null) {
            throw new IllegalArgumentException("the report must say how it relates");
        }
        if (subjectClaimId != null && subjectClaimId.isBlank()) {
            throw new IllegalArgumentException(
                    "the subject claim id must be absent or a real id, not blank");
        }
        if (confidenceBasis == null) {
            throw new IllegalArgumentException("the report must say what its confidence rests on");
        }
        if (derivedFrom == null) {
            derivedFrom = List.of();
        }
        if (limitations == null) {
            limitations = List.of();
        }
    }

    /**
     * True when the report rests on records this process supplied earlier.
     *
     * <p>A report whose lineage names the records Chain A sent to Chain B is the same
     * information coming back, not a second, independent observation of it. The receiver
     * uses this to refuse to treat a returning proposition as corroboration.</p>
     *
     * @param supplied the ids of the records this process supplied to the peer
     * @return true when the lineage intersects them
     */
    public boolean returnsKnowledge(List<String> supplied) {
        if (supplied == null || supplied.isEmpty() || derivedFrom.isEmpty()) {
            return false;
        }
        return derivedFrom.stream().anyMatch(supplied::contains);
    }

    /**
     * The ids of the records the report rests on, that this process supplied.
     *
     * @param supplied the ids of the records this process supplied to the peer
     * @return the intersection, in the report's order
     */
    public List<String> returnedRecords(List<String> supplied) {
        if (supplied == null || supplied.isEmpty() || derivedFrom.isEmpty()) {
            return List.of();
        }
        return derivedFrom.stream().filter(supplied::contains).toList();
    }
}
