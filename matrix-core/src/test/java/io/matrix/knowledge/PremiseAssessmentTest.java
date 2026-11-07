package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W50, wave 50, turn 3 — a user assertion is evaluated against the stored
 * knowledge, and never becomes a source of its own.
 *
 * <p>What the test asserts about the SYSTEM, and not about the code:
 *
 * <ul>
 *   <li>a premise the workspace contradicts is reported as CONTRADICTED, and
 *       the finding names the contradicting claim, its source, and says the
 *       evidence contradicts rather than merely fails to support;</li>
 *   <li>a premise the workspace knows nothing about is UNSUPPORTED — the
 *       absence of evidence is not a contradiction, and the reason says so;</li>
 *   <li>a premise the workspace holds as a claim is SUPPORTED, and the
 *       evidence carries the claim and its source;</li>
 *   <li>the assessment writes NOTHING: the claims, the evidence and the
 *       statuses are exactly what they were, and the assertion is not
 *       promoted;</li>
 *   <li>when the workspace holds contradicting accounts that do not settle
 *       each other, the premise is UNRESOLVED and the system does not say
 *       which side is right.</li>
 * </ul>
 *
 * <p>The workspace is built by hand rather than loaded from a package, so
 * that the claims under assessment are exactly the ones the test means.
 */
class PremiseAssessmentTest {

    @TempDir
    Path dir;

    /** The workspace the claims live in. */
    private static final String WORKSPACE = "tectonics";

    /** The assertion under assessment. */
    private static final String ASSERTION = "Plate motion is driven only by mantle convection.";

    /** The claim that states the assertion, and supports it. */
    private static final String STATED = "Plate motion is driven only by mantle convection";

    /** The claim that denies it. */
    private static final String CONTRADICTING =
            "Slab pull, not mantle convection, accounts for almost all of the force driving "
                    + "plate tectonics";

    /** The second claim that denies it, and does not agree with the first. */
    private static final String CONTRADICTING_SECOND =
            "Slab pull, rather than mantle convection, drives most of the motion of the plates";

    // ── the tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a premise the stored evidence contradicts is reported as contradicted")
    void aPremiseThatTheEvidenceContradictsIsReportedAsContradicted() {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("contradicted.sqlite").toString())) {
            store(workbench, "clm-stated", "cpt-motion", "Plate motion", STATED,
                    "src-textbook", "An Introduction to Plate Tectonics",
                    "https://example.org/textbook/chapter-4", STATED + ".");
            store(workbench, "clm-pull", "cpt-slab-pull", "Slab pull", CONTRADICTING,
                    "src-review", "Slab pull and the driving force of plate tectonics",
                    "https://example.org/review/slab-pull", CONTRADICTING + ".");

            PremiseAssessment.Assessment assessment =
                    PremiseAssessment.assess(workbench, WORKSPACE, ASSERTION);

            assertThat(assessment.findings()).hasSize(1);
            PremiseAssessment.Finding finding = assessment.findings().get(0);

            assertThat(finding.verdict()).isEqualTo(PremiseAssessment.Verdict.CONTRADICTED);
            assertThat(finding.premise())
                    .as("the premise keeps the caller's own words")
                    .isEqualTo("Plate motion is driven only by mantle convection");
            assertThat(finding.disputedPremise())
                    .as("the finding says which words are disputed")
                    .isNotBlank();

            assertThat(finding.reason())
                    .as("the reason names the contradicting claim and its source")
                    .contains("clm-pull")
                    .contains("Slab pull and the driving force of plate tectonics")
                    .contains("contradicts the premise rather than merely failing to support it");

            assertThat(finding.evidence()).anySatisfy(reference -> {
                assertThat(reference.claimId()).isEqualTo("clm-pull");
                assertThat(reference.sourceId()).isEqualTo("src-review");
                assertThat(reference.relation()).isEqualTo("CONTRADICTS");
            });
        }
    }

    @Test
    @DisplayName("a premise with no related evidence is unsupported, not contradicted")
    void aPremiseWithNoRelatedEvidenceIsUnsupportedNotContradicted() {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("unsupported.sqlite").toString())) {
            store(workbench, "clm-stated", "cpt-motion", "Plate motion", STATED,
                    "src-textbook", "An Introduction to Plate Tectonics",
                    "https://example.org/textbook/chapter-4", STATED + ".");
            store(workbench, "clm-pull", "cpt-slab-pull", "Slab pull", CONTRADICTING,
                    "src-review", "Slab pull and the driving force of plate tectonics",
                    "https://example.org/review/slab-pull", CONTRADICTING + ".");

            PremiseAssessment.Assessment assessment = PremiseAssessment.assess(workbench,
                    WORKSPACE, "The boiling point of mercury is 357 degrees Celsius.");

            assertThat(assessment.findings()).hasSize(1);
            PremiseAssessment.Finding finding = assessment.findings().get(0);

            assertThat(finding.verdict()).isEqualTo(PremiseAssessment.Verdict.UNSUPPORTED);
            assertThat(finding.reason())
                    .as("the absence of evidence is not a contradiction, and the reason says so")
                    .contains("absence of evidence is not a contradiction");
            assertThat(finding.evidence()).isEmpty();
            assertThat(finding.disputedPremise()).isNull();
        }
    }

    @Test
    @DisplayName("a premise that matches a stored claim is supported")
    void aPremiseThatMatchesAStoredClaimIsSupported() {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("supported.sqlite").toString())) {
            store(workbench, "clm-stated", "cpt-motion", "Plate motion", STATED,
                    "src-textbook", "An Introduction to Plate Tectonics",
                    "https://example.org/textbook/chapter-4", STATED + ".");

            PremiseAssessment.Assessment assessment =
                    PremiseAssessment.assess(workbench, WORKSPACE, ASSERTION);

            assertThat(assessment.findings()).hasSize(1);
            PremiseAssessment.Finding finding = assessment.findings().get(0);

            assertThat(finding.verdict()).isEqualTo(PremiseAssessment.Verdict.SUPPORTED);
            assertThat(finding.evidence()).anySatisfy(reference -> {
                assertThat(reference.claimId()).isEqualTo("clm-stated");
                assertThat(reference.sourceId()).isEqualTo("src-textbook");
            });
            assertThat(finding.reason()).contains("clm-stated");
        }
    }

    @Test
    @DisplayName("the assessment does not change the workspace")
    void theAssessmentDoesNotChangeTheWorkspace() {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("unchanged.sqlite").toString())) {
            store(workbench, "clm-stated", "cpt-motion", "Plate motion", STATED,
                    "src-textbook", "An Introduction to Plate Tectonics",
                    "https://example.org/textbook/chapter-4", STATED + ".");
            store(workbench, "clm-pull", "cpt-slab-pull", "Slab pull", CONTRADICTING,
                    "src-review", "Slab pull and the driving force of plate tectonics",
                    "https://example.org/review/slab-pull", CONTRADICTING + ".");

            KnowledgeSnapshot before = workbench.workspace(WORKSPACE);

            PremiseAssessment.Assessment assessment =
                    PremiseAssessment.assess(workbench, WORKSPACE, ASSERTION);

            KnowledgeSnapshot after = workbench.workspace(WORKSPACE);

            assertThat(after.claims()).hasSameSizeAs(before.claims());
            assertThat(after.evidence()).hasSameSizeAs(before.evidence());
            assertThat(after.sources()).hasSameSizeAs(before.sources());
            assertThat(statusesOf(after)).isEqualTo(statusesOf(before));
            assertThat(after.claims())
                    .as("the assertion itself is not stored as a claim")
                    .noneMatch(claim -> claim.proposition().equals(ASSERTION));

            assertThat(assessment.promoted())
                    .as("a user assertion is never promoted")
                    .isFalse();
            assertThat(assessment.knowledgeEffect())
                    .as("the assessment says plainly that the store was not touched")
                    .contains("evaluated")
                    .contains("not")
                    .contains("unchanged");
        }
    }

    @Test
    @DisplayName("a premise with evidence on both sides is unresolved")
    void aPremiseWithEvidenceOnBothSidesIsUnresolved() {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("unresolved.sqlite").toString())) {
            store(workbench, "clm-stated", "cpt-motion", "Plate motion", STATED,
                    "src-textbook", "An Introduction to Plate Tectonics",
                    "https://example.org/textbook/chapter-4", STATED + ".");
            store(workbench, "clm-pull", "cpt-slab-pull", "Slab pull", CONTRADICTING,
                    "src-review", "Slab pull and the driving force of plate tectonics",
                    "https://example.org/review/slab-pull", CONTRADICTING + ".");
            store(workbench, "clm-pull-2", "cpt-slab-pull-2", "Slab pull and plate motion",
                    CONTRADICTING_SECOND, "src-second-review",
                    "A second account of the driving force of plate tectonics",
                    "https://example.org/review/slab-pull-2", CONTRADICTING_SECOND + ".");

            PremiseAssessment.Assessment assessment =
                    PremiseAssessment.assess(workbench, WORKSPACE, ASSERTION);

            assertThat(assessment.findings()).hasSize(1);
            PremiseAssessment.Finding finding = assessment.findings().get(0);

            assertThat(finding.verdict()).isEqualTo(PremiseAssessment.Verdict.UNRESOLVED);
            assertThat(finding.reason())
                    .as("the workspace does not settle the disagreement")
                    .contains("does not say which of them is right")
                    .doesNotContain("the premise is true", "the premise is false",
                            "the premise is wrong");
            assertThat(finding.evidence())
                    .as("both contradicting accounts are named")
                    .anyMatch(reference -> reference.claimId().equals("clm-pull"))
                    .anyMatch(reference -> reference.claimId().equals("clm-pull-2"));
        }
    }

    // ── the helpers ──────────────────────────────────────────────────────────

    /**
     * A claim with its own source and its own evidence, written into the
     * workspace.
     *
     * @param workbench the workbench
     * @param claimId the claim's identifier
     * @param conceptId the concept's identifier
     * @param conceptName the concept's name
     * @param proposition what the claim asserts
     * @param sourceId the source's identifier
     * @param sourceTitle the source's title
     * @param locator where the source is
     * @param excerpt the text of the evidence
     * @return the claim
     */
    private Claim store(KnowledgeWorkbench workbench, String claimId, String conceptId,
            String conceptName, String proposition, String sourceId, String sourceTitle,
            String locator, String excerpt) {
        String now = "2026-11-02T00:00:00Z";
        Concept concept = new Concept(conceptId, WORKSPACE, conceptName,
                "What the workspace holds about " + conceptName + ".", "geology", now);
        Source source = new Source(sourceId, WORKSPACE, SourceKind.PUBLICATION, sourceTitle,
                locator, 80, 70, excerpt, now, "hash-" + sourceId, null, "en", "text");
        Claim claim = new Claim(claimId, WORKSPACE, conceptId, proposition, null, null,
                ClaimStatus.SUPPORTED, 0.8, 1, sourceId, "extracted from a source", "manual",
                ClaimWarrant.NONE, DerivationStatus.NOT_DERIVED, now, now);
        Evidence evidence = new Evidence("evd-" + claimId, WORKSPACE, claimId, sourceId,
                EvidenceRelation.SUPPORTS, excerpt, 80, now);
        workbench.storeForValidation().applyChanges(WORKSPACE,
                new KnowledgeChanges(List.of(source), List.of(claim), List.of(evidence),
                        List.of(), List.of()),
                List.of(concept));
        return claim;
    }

    /**
     * The status of every claim of a workspace, by claim id.
     *
     * @param snapshot the workspace
     * @return the statuses
     */
    private static Map<String, ClaimStatus> statusesOf(KnowledgeSnapshot snapshot) {
        Map<String, ClaimStatus> statuses = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            statuses.put(claim.id(), claim.status());
        }
        return statuses;
    }
}
