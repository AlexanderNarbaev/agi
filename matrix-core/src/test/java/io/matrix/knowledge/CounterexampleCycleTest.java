package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W43.2 — the counterexample cycle, end to end.
 *
 * <p>This is the mission's §5 scenario as a test: an example, a
 * counterexample, the question of whether the claim still holds, the
 * narrower formulation the evidence supports, the acceptance of it, and the
 * history that survives the process being stopped and started again.
 *
 * <p>What the test asserts about the SYSTEM, and not about the code:
 *
 * <ul>
 *   <li>one positive example does not establish a universal claim — the
 *       standing says "supported by an example", and the reason says what
 *       that does and does not establish;</li>
 *   <li>a counterexample with a recorded condition is read as a MISSING
 *       CONDITION, not as a refutation, because the claim states a scope
 *       and the condition is not in it;</li>
 *   <li>the proposed narrowing quotes the condition verbatim and does not
 *       invent semantic content;</li>
 *   <li>accepting the refinement KEEPS the original claim, superseded and
 *       still readable, and stores the revision that links them;</li>
 *   <li>rejecting a refinement keeps the original AND keeps the fact that
 *       a refinement was considered;</li>
 *   <li>everything survives a restart, and the assessment of an unchanged
 *       state is the same every time.</li>
 * </ul>
 */
class CounterexampleCycleTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench(Path db) {
        return KnowledgeWorkbench.at(db.toString());
    }

    /**
     * A workspace with ONE claim, no evidence and a stated scope.
     *
     * <p>The bundled photosynthesis package is not used for the cycle tests:
     * its claims arrive with supporting evidence already attached, and the
     * point of the first test is that a claim with NO supporting source
     * becomes "supported by an example only" — which is a different
     * situation and a different assertion.
     */
    private Claim seedClaim(KnowledgeWorkbench wb, String conceptId) {
        Concept concept = new Concept(conceptId, "plants", "Plant respiration",
                "the process by which a plant breaks down sugars", "biology",
                "2026-11-02T00:00:00Z");
        Claim claim = new Claim("clm-" + conceptId, "plants", conceptId,
                "Plants release oxygen during the day",
                "in daylight, in the light",
                "a controlled observation of a plant",
                ClaimStatus.HYPOTHESIS, 0.0, 1, null,
                "manual", "manual", ClaimWarrant.NONE, DerivationStatus.NOT_DERIVED,
                "2026-11-02T00:00:00Z", "2026-11-02T00:00:00Z");
        wb.storeForValidation().applyChanges("plants",
                new KnowledgeChanges(List.of(), List.of(claim), List.of(), List.of(), List.of()),
                List.of(concept));
        return claim;
    }

    @Test
    @DisplayName("the counterexample cycle survives a restart")
    void theCounterexampleCycleSurvivesARestart() {
        Path db = dir.resolve("workbench.sqlite");
        String claimId;
        String originalProposition;
        String proposed;

        // ── the first process ────────────────────────────────────────────
        try (KnowledgeWorkbench wb = workbench(db)) {
            Claim claim = seedClaim(wb, "plants-release-oxygen");
            claimId = claim.id();
            originalProposition = claim.proposition();
            assertThat(claim.scope())
                    .as("the claim states a scope, which is what the counterexample is read "
                            + "against")
                    .isNotBlank();

            // ── a positive example ────────────────────────────────────────
            Evidence example = wb.recordExample("plants", claimId,
                    "a geranium on a windowsill releases oxygen in the light",
                    "in daylight", false);
            assertThat(example.relation()).isEqualTo(EvidenceRelation.EXEMPLIFIES);

            ClaimAssessment afterExample = wb.assessClaim("plants", claimId);
            assertThat(afterExample.standing()).isEqualTo(ClaimStatus.SUPPORTED);
            assertThat(afterExample.exemplifying()).hasSize(1);
            assertThat(afterExample.standingReason())
                    .as("an example is consistent with the claim and does not establish it")
                    .contains("does not establish it in general");
            assertThat(afterExample.hasProposal()).isFalse();

            // ── a counterexample, with the condition it was made under ────
            Evidence counterexample = wb.recordExample("plants", claimId,
                    "a plant kept in the dark does not release oxygen",
                    "without light", true);
            assertThat(counterexample.relation()).isEqualTo(EvidenceRelation.CONTRADICTS);

            ClaimAssessment afterCounterexample = wb.assessClaim("plants", claimId);
            assertThat(afterCounterexample.standing()).isEqualTo(ClaimStatus.REFUTED);
            assertThat(afterCounterexample.counterexampleReading())
                    .isEqualTo(CounterexampleReading.REVEALS_MISSING_CONDITION);
            assertThat(afterCounterexample.contradicting()).hasSize(1);
            assertThat(afterCounterexample.contradicting().get(0).excerpt())
                    .contains("does not release oxygen");
            assertThat(afterCounterexample.readingReason()).contains("without light");

            // The narrowing quotes the condition and invents nothing.
            proposed = afterCounterexample.proposedRefinement();
            assertThat(proposed).isNotNull();
            assertThat(proposed).startsWith(originalProposition);
            assertThat(proposed).contains("without light");
            assertThat(afterCounterexample.refinementReason()).contains("verbatim");

            // ── accepting the refinement ───────────────────────────────────
            KnowledgeRevision revision = wb.acceptRefinement("plants", claimId, proposed);
            assertThat(revision.kind()).isEqualTo(RevisionKind.REFINEMENT);
            assertThat(revision.targetId()).isEqualTo(claimId);
            assertThat(revision.evidenceIds()).containsExactly(counterexample.id());
            assertThat(revision.summary()).contains(proposed);

            // The original claim is SUPERSEDED, not deleted, and still says
            // what it said.
            Claim superseded = wb.workspace("plants").claims().stream()
                    .filter(c -> c.id().equals(claimId)).findFirst().orElseThrow();
            assertThat(superseded.status()).isEqualTo(ClaimStatus.SUPERSEDED);
            assertThat(superseded.proposition()).isEqualTo(originalProposition);
            assertThat(superseded.version()).isEqualTo(claim.version() + 1);

            // And the narrower claim is there beside it.
            assertThat(wb.workspace("plants").claims())
                    .anyMatch(c -> c.proposition().equals(proposed));
        }

        // ── the second process ───────────────────────────────────────────
        try (KnowledgeWorkbench restarted = workbench(db)) {
            Claim superseded = restarted.workspace("plants").claims().stream()
                    .filter(c -> c.id().equals(claimId)).findFirst().orElseThrow();
            assertThat(superseded.status())
                    .as("the earlier model was used and later found insufficient — that fact "
                            + "survives the restart")
                    .isEqualTo(ClaimStatus.SUPERSEDED);
            assertThat(superseded.proposition()).isEqualTo(originalProposition);

            assertThat(restarted.workspace("plants").claims())
                    .anyMatch(c -> c.proposition().equals(proposed));

            assertThat(restarted.workspace("plants").revisions())
                    .anyMatch(r -> r.kind() == RevisionKind.REFINEMENT && r.targetId().equals(claimId));

            // The assessment of the unchanged state is the same after the
            // restart: the reasoning result is read from the store, not from
            // a cache.
            ClaimAssessment afterRestart = restarted.assessClaim("plants", claimId);
            assertThat(afterRestart.standing()).isEqualTo(ClaimStatus.REFUTED);
            assertThat(afterRestart.counterexampleReading())
                    .isEqualTo(CounterexampleReading.REVEALS_MISSING_CONDITION);
            assertThat(afterRestart.proposedRefinement()).isEqualTo(proposed);
        }
    }

    @Test
    @DisplayName("a counterexample without a condition leaves the reading open")
    void aCounterexampleWithoutAConditionLeavesTheReadingOpen() {
        try (KnowledgeWorkbench wb = workbench(dir.resolve("w2.sqlite"))) {
            Claim claim = seedClaim(wb, "plants-release-oxygen-2");

            wb.recordExample("plants", claim.id(),
                    "something was observed", null, true);

            ClaimAssessment assessment = wb.assessClaim("plants", claim.id());
            assertThat(assessment.counterexampleReading())
                    .isEqualTo(CounterexampleReading.INCONCLUSIVE);
            assertThat(assessment.alternativeExplanations())
                    .as("the system says which explanations it could not exclude, rather than "
                            + "choosing one")
                    .isNotEmpty();
            assertThat(assessment.hasProposal())
                    .as("the evidence does not support a narrowing, so none is proposed")
                    .isFalse();
            assertThat(assessment.unresolved()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("a rejected refinement keeps the original AND the fact of the rejection")
    void aRejectedRefinementKeepsTheOriginalAndTheFactOfTheRejection() {
        try (KnowledgeWorkbench wb = workbench(dir.resolve("w3.sqlite"))) {
            Claim claim = seedClaim(wb, "plants-release-oxygen-3");

            KnowledgeRevision revision = wb.rejectRefinement("plants", claim.id(),
                    "the counterexample is not reliable");
            assertThat(revision.kind()).isEqualTo(RevisionKind.REJECTED_REFINEMENT);
            assertThat(revision.summary()).contains("the counterexample is not reliable");

            Claim unchanged = wb.workspace("plants").claims().stream()
                    .filter(c -> c.id().equals(claim.id())).findFirst().orElseThrow();
            assertThat(unchanged.status())
                    .as("a rejected refinement leaves the claim as it was")
                    .isEqualTo(claim.status());
            assertThat(wb.workspace("plants").revisions())
                    .anyMatch(r -> r.kind() == RevisionKind.REJECTED_REFINEMENT);
        }
    }

    @Test
    @DisplayName("the assessment of an unchanged state is the same every time")
    void theAssessmentOfAnUnchangedStateIsTheSameEveryTime() {
        try (KnowledgeWorkbench wb = workbench(dir.resolve("w4.sqlite"))) {
            Claim claim = seedClaim(wb, "plants-release-oxygen-4");
            wb.recordExample("plants", claim.id(), "an observation", "a condition", true);

            ClaimAssessment first = wb.assessClaim("plants", claim.id());
            ClaimAssessment second = wb.assessClaim("plants", claim.id());

            assertThat(second.standing()).isEqualTo(first.standing());
            assertThat(second.counterexampleReading()).isEqualTo(first.counterexampleReading());
            assertThat(second.proposedRefinement()).isEqualTo(first.proposedRefinement());
            assertThat(second.contradicting()).hasSameSizeAs(first.contradicting());
        }
    }
}
