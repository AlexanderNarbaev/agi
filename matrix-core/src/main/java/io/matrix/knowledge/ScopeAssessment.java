package io.matrix.knowledge;

import java.util.List;

/**
 * CRJ-01 / COMPETENCE-REACH — the full verdict on a competence's scope for a question.
 *
 * <p><b>Why this is a separate record.</b> The served path's verdict type is
 * {@link SupportAdequacy}, and it must not change shape: the ADAPTIVE-COMPETENCE milestone's
 * retained evidence and its gate read that record, and a milestone that changed the shape of a
 * verdict type already in a retained artefact would make that artefact unreproducible. This
 * record therefore carries everything the comparison needs — including the claims the scope does
 * <b>not</b> carry, which the anchor rule never needed and the claim-sufficiency rule does —
 * and {@link #toAdequacy()} narrows it to the record the served path already uses.</p>
 *
 * <p><b>What the ids mean.</b> {@code workspaceCitedIds} are the claims the workspace's own
 * reading of the question would cite, best first. {@code supportCitedIds} are the claims the
 * scope's reading would cite. {@code missingClaimIds} are the former without the latter — the
 * evidence the scope would have to carry and does not.</p>
 *
 * @param applicable         whether the scope may serve the question
 * @param reason             the sentence a reader can act on
 * @param criterion          the rule the decision was made by
 * @param bestClaimId        the claim the unrestricted reading would cite first, or null
 * @param bestCoverage       the coverage of that claim
 * @param supportBestCoverage the best coverage the scope carries for the question
 * @param supportClaimIds    the claims the scope holds
 * @param supportCitedIds    the claims the scope's reading would cite
 * @param workspaceCitedIds  the claims the workspace's reading would cite
 * @param missingClaimIds    the cited claims the scope does not carry
 */
public record ScopeAssessment(
        boolean applicable,
        String reason,
        ScopeCriterion criterion,
        String bestClaimId,
        double bestCoverage,
        double supportBestCoverage,
        List<String> supportClaimIds,
        List<String> supportCitedIds,
        List<String> workspaceCitedIds,
        List<String> missingClaimIds) {

    public ScopeAssessment {
        supportClaimIds = List.copyOf(supportClaimIds == null ? List.of() : supportClaimIds);
        supportCitedIds = List.copyOf(supportCitedIds == null ? List.of() : supportCitedIds);
        workspaceCitedIds =
                List.copyOf(workspaceCitedIds == null ? List.of() : workspaceCitedIds);
        missingClaimIds = List.copyOf(missingClaimIds == null ? List.of() : missingClaimIds);
    }

    /**
     * The verdict in the shape the served path already uses.
     *
     * @return the adequacy verdict
     */
    public SupportAdequacy toAdequacy() {
        return new SupportAdequacy(applicable, reason, bestClaimId, bestCoverage,
                supportBestCoverage, supportClaimIds, supportCitedIds);
    }

    /**
     * The verdict for a scope that holds nothing of the workspace.
     *
     * @param criterion the rule that was applied
     * @param reason    the sentence a reader can act on
     * @return the assessment
     */
    public static ScopeAssessment none(ScopeCriterion criterion, String reason) {
        return new ScopeAssessment(false, reason, criterion, null, 0.0, 0.0, List.of(), List.of(),
                List.of(), List.of());
    }
}
