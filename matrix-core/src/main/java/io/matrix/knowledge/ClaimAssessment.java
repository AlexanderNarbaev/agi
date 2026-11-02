package io.matrix.knowledge;

import java.util.List;

/**
 * The reasoning result of "does this claim still hold?" (RECON-W43.0).
 *
 * <p>This is the mission's §3.5 reasoning result, in the form the knowledge
 * core can actually produce: the question, the evidence that was retrieved,
 * the contradictions that were detected, the result, the scope it applies
 * to, and the questions it leaves open. It is deliberately NOT a confidence
 * number — the mission's §14 forbids a confidence number without calibration
 * evidence, and there is no calibration evidence for one.
 *
 * <p>What a reader can do with it: see WHICH evidence supports the claim,
 * WHICH contradicts it, WHERE the contradiction came from, WHY the system
 * read the contradiction the way it did, what the system could NOT decide,
 * and — when the evidence supports one — the narrower formulation the system
 * proposes.
 */
public record ClaimAssessment(
        String workspaceId,
        String claimId,
        String proposition,
        String scope,
        ClaimStatus standing,
        String standingReason,
        CounterexampleReading counterexampleReading,
        String readingReason,
        List<String> alternativeExplanations,
        List<EvidenceRef> supporting,
        List<EvidenceRef> contradicting,
        List<EvidenceRef> qualifying,
        List<EvidenceRef> exemplifying,
        List<EvidenceRef> insufficient,
        List<String> unresolved,
        String proposedRefinement,
        String refinementReason,
        String assessedAt) {

    /**
     * One piece of evidence, with the source it came from.
     *
     * @param evidenceId the evidence id
     * @param relation the relation
     * @param sourceId the source id
     * @param sourceTitle the source's title
     * @param sourceKind the source's kind
     * @param excerpt the exact text the evidence rests on
     * @param recordedAt when the evidence was recorded
     */
    public record EvidenceRef(
            String evidenceId,
            String relation,
            String sourceId,
            String sourceTitle,
            String sourceKind,
            String excerpt,
            String recordedAt) {
    }

    /** Whether the assessment proposes a narrower formulation. */
    public boolean hasProposal() {
        return proposedRefinement != null && !proposedRefinement.isBlank();
    }

    /**
     * The assessment as a map, for the REST layer.
     *
     * @return the map
     */
    public java.util.Map<String, Object> asMap() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("workspaceId", workspaceId);
        out.put("claimId", claimId);
        out.put("proposition", proposition);
        out.put("scope", scope);
        out.put("standing", standing == null ? null : standing.name());
        out.put("standingReason", standingReason);
        out.put("counterexampleReading",
                counterexampleReading == null ? null : counterexampleReading.name());
        out.put("readingReason", readingReason);
        out.put("alternativeExplanations", alternativeExplanations);
        out.put("supporting", supporting.stream().map(e -> e.evidenceId()).toList());
        out.put("contradicting", contradicting.stream().map(e -> e.evidenceId()).toList());
        out.put("qualifying", qualifying.stream().map(e -> e.evidenceId()).toList());
        out.put("exemplifying", exemplifying.stream().map(e -> e.evidenceId()).toList());
        out.put("insufficient", insufficient.stream().map(e -> e.evidenceId()).toList());
        out.put("supportingDetail", supporting);
        out.put("contradictingDetail", contradicting);
        out.put("qualifyingDetail", qualifying);
        out.put("exemplifyingDetail", exemplifying);
        out.put("insufficientDetail", insufficient);
        out.put("unresolved", unresolved);
        out.put("proposedRefinement", proposedRefinement);
        out.put("refinementReason", refinementReason);
        out.put("assessedAt", assessedAt);
        return out;
    }
}
