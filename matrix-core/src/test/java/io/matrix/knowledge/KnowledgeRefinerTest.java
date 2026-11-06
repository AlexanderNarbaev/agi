package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the conceptual-change path.
 *
 * <p>A correction must leave the workspace better informed and no less
 * informed than before: the earlier claim must still be there, marked as
 * superseded, with a revision that says what changed and why. A system that
 * overwrites what it believed has lost the ability to explain itself, and a
 * system that deletes it has lost the ability to be corrected honestly.
 */
class KnowledgeRefinerTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    private String photosynthesisId() {
        return workbench.workspace("photosynthesis").concepts().stream()
                .filter(c -> c.name().equals("Photosynthesis")).findFirst().orElseThrow().id();
    }

    private Claim claimWith(String text) {
        return workbench.workspace("photosynthesis").claims().stream()
                .filter(c -> c.proposition().contains(text)).findFirst().orElseThrow();
    }

    private KnowledgeRefiner.Refinement refinement(String supersededClaimId, String proposition) {
        return new KnowledgeRefiner.Refinement(
                photosynthesisId(),
                supersededClaimId,
                proposition,
                "In green plants, in light",
                "general biology",
                ClaimStatus.SUPPORTED,
                0.8,
                SourceKind.HUMAN_ASSERTION,
                "A measured outcome from the greenhouse",
                "urn:experiment:greenhouse-2026-10-31",
                "Oxygen release fell by a third when the lamp was dimmed.",
                60,
                95,
                RevisionKind.CORRECTION,
                "The correction adds the condition under which the earlier claim holds.",
                List.of("the rate falls with the light level"),
                List.of("plants still photosynthesise in light"),
                List.of("the earlier claim held without mentioning the light level"),
                List.of("why a dimmer day lowers the measured oxygen release"),
                null, null);
    }

    @Test
    @DisplayName("a correction supersedes the earlier claim without deleting it")
    void aCorrectionSupersedesWithoutDeleting() {
        Claim original = claimWith("Oxygenic photosynthesis releases oxygen");
        int claimsBefore = workbench.workspace("photosynthesis").claims().size();

        workbench.refine("photosynthesis", refinement(original.id(),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls."));

        KnowledgeSnapshot after = workbench.workspace("photosynthesis");
        // The earlier claim is still there, marked superseded.
        Claim superseded = after.claims().stream()
                .filter(c -> c.id().equals(original.id())).findFirst().orElseThrow();
        assertThat(superseded.status()).isEqualTo(ClaimStatus.SUPERSEDED);
        assertThat(superseded.proposition()).isEqualTo(original.proposition());
        assertThat(superseded.version()).isEqualTo(original.version() + 1);
        // And a new claim was added.
        assertThat(after.claims()).hasSize(claimsBefore + 1);
    }

    @Test
    @DisplayName("the corrected claim carries its source and is therefore supported")
    void theCorrectedClaimCarriesItsSource() {
        Claim original = claimWith("Oxygenic photosynthesis releases oxygen");

        KnowledgeChanges changes = workbench.refine("photosynthesis", refinement(original.id(),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls."));

        assertThat(changes.sources()).hasSize(1);
        Source source = changes.sources().get(0);
        assertThat(source.kind()).isEqualTo(SourceKind.HUMAN_ASSERTION);
        assertThat(source.locator()).isNotBlank();

        Claim corrected = changes.claims().stream()
                .filter(c -> !c.id().equals(original.id())).findFirst().orElseThrow();
        assertThat(corrected.status()).isEqualTo(ClaimStatus.SUPPORTED);
        assertThat(corrected.introducedBySourceId()).isEqualTo(source.id());

        // The evidence link is what makes it supported rather than a draft.
        assertThat(changes.evidence()).hasSize(1);
        assertThat(changes.evidence().get(0).claimId()).isEqualTo(corrected.id());
        assertThat(changes.evidence().get(0).sourceId()).isEqualTo(source.id());
    }

    @Test
    @DisplayName("the revision records what changed")
    void theRevisionRecordsWhatChanged() {
        Claim original = claimWith("Oxygenic photosynthesis releases oxygen");

        KnowledgeChanges changes = workbench.refine("photosynthesis", refinement(original.id(),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls."));

        assertThat(changes.revisions()).hasSize(1);
        KnowledgeRevision revision = changes.revisions().get(0);
        assertThat(revision.kind()).isEqualTo(RevisionKind.CORRECTION);
        assertThat(revision.targetId()).isEqualTo(photosynthesisId());
        assertThat(revision.addedFacts()).isNotEmpty();
        assertThat(revision.stillValidAssumptions()).isNotEmpty();
        assertThat(revision.noLongerValidAssumptions()).isNotEmpty();
        assertThat(revision.newlyExplainedCases()).isNotEmpty();
        assertThat(revision.evidenceIds()).isNotEmpty();
    }

    @Test
    @DisplayName("the whole history survives a restart")
    void theHistorySurvivesARestart() {
        Claim original = claimWith("Oxygenic photosynthesis releases oxygen");
        workbench.refine("photosynthesis", refinement(original.id(),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls."));
        workbench.close();

        try (KnowledgeWorkbench restarted = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString())) {
            KnowledgeSnapshot after = restarted.workspace("photosynthesis");
            assertThat(after.claims().stream().filter(c -> c.id().equals(original.id())))
                    .singleElement().satisfies(c -> assertThat(c.status())
                            .isEqualTo(ClaimStatus.SUPERSEDED));
            assertThat(after.revisions()).hasSize(2);
            assertThat(after.sources()).hasSize(5);
        }
    }

    @Test
    @DisplayName("a refinement that names something the workspace does not hold is refused")
    void anUnknownReferenceIsRefused() {
        assertThatThrownBy(() -> workbench.refine("photosynthesis",
                refinement("cl-does-not-exist", "A new claim.")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holds no claim");

        assertThatThrownBy(() -> workbench.refine("photosynthesis",
                new KnowledgeRefiner.Refinement("c-does-not-exist", null, "A new claim.", null, null,
                        ClaimStatus.SUPPORTED, 0.5, SourceKind.HUMAN_ASSERTION, "A source", null, null,
                        50, 50, RevisionKind.CORRECTION, "A summary.", List.of(), List.of(), List.of(),
                        List.of(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holds no concept");
    }

    @Test
    @DisplayName("a claim that asserts an established status without evidence is refused")
    void aClaimWithoutEvidenceIsRefused() {
        KnowledgeRefiner.Refinement unverifiable = new KnowledgeRefiner.Refinement(
                photosynthesisId(), null, "An assertion with no source.", null, null,
                ClaimStatus.VERIFIED, 0.9, SourceKind.HUMAN_ASSERTION, "A source", null, null,
                50, 50, RevisionKind.CORRECTION, "A summary.", List.of(), List.of(), List.of(), List.of(),
                null, null);

        // The refiner always attaches the source, so the change itself is
        // sound; the store's rule is what refuses a change that would assert
        // an established status with nothing behind it.
        assertThatThrownBy(() -> {
            KnowledgeChanges changes = new KnowledgeRefiner().refine("photosynthesis",
                    workbench.workspace("photosynthesis"), unverifiable);
            workbench.workspace("photosynthesis");
            storeOf(workbench).applyChanges("photosynthesis",
                    new KnowledgeChanges(changes.sources(), changes.claims(), List.of(),
                            changes.representations(), changes.revisions()));
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carries no evidence");
    }

    @Test
    @DisplayName("a refinement that changes what is said also changes the account the reader sees")
    void aRefinementUpdatesTheAccount() {
        Claim original = claimWith("Oxygenic photosynthesis releases oxygen");
        KnowledgeRefiner.Refinement withAccount = new KnowledgeRefiner.Refinement(
                photosynthesisId(), original.id(),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls.",
                "Green plants in light; the rate depends on the light level", "general biology",
                ClaimStatus.SUPPORTED, 0.8, SourceKind.OBSERVATION,
                "Greenhouse light series", "urn:experiment:greenhouse-2026-10-31",
                "Oxygen release fell by a third when the lamp was dimmed.", 70, 100,
                RevisionKind.CORRECTION, "The correction adds the light level as a condition.",
                List.of("the rate falls with the light level"),
                List.of("plants still photosynthesise in light"),
                List.of("the earlier claim held without mentioning the light level"),
                List.of("why a dimmer day lowers the measured oxygen release"),
                "Oxygenic photosynthesis releases oxygen at a rate that falls as the light level falls.",
                AbstractionLevel.INTRODUCTORY);

        KnowledgeChanges changes = workbench.refine("photosynthesis", withAccount);

        // The refined account was written and it refines the earlier one.
        assertThat(changes.representations()).hasSize(1);
        Representation refined = changes.representations().get(0);
        assertThat(refined.refinesRepresentationId()).isNotBlank();
        assertThat(refined.statement()).contains("light level");

        // The earlier account is still there, in the chain.
        KnowledgeSnapshot after = workbench.workspace("photosynthesis");
        assertThat(after.representations()).extracting(Representation::id)
                .contains(refined.refinesRepresentationId());

        // And the answer now uses the refined account.
        Answer answer = workbench.ask("photosynthesis", "What is photosynthesis?",
                AbstractionLevel.INTRODUCTORY);
        assertThat(answer.statement()).contains("light level");
    }

    private static KnowledgeStore storeOf(KnowledgeWorkbench workbench) {
        try {
            java.lang.reflect.Method method = KnowledgeWorkbench.class.getDeclaredMethod("store");
            method.setAccessible(true);
            return (KnowledgeStore) method.invoke(workbench);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
