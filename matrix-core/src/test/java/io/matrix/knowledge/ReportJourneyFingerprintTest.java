package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.report.WorkspaceFingerprint;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeSnapshot;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2 — the fingerprint of the workspace, and what it has to cover.
 *
 * <p><b>Why this is a test of its own.</b> The fingerprint is the replay's whole safety
 * condition: it is what says "the workspace still holds what the journey read". A field
 * the fingerprint does not cover is a field whose change the replay cannot see, and the
 * replay would then serve an answer the workspace would no longer give. The review that
 * came back on the closed loop found exactly that: the scope, the context and the concept
 * a claim belongs to were not covered.</p>
 *
 * <p><b>What is pinned here.</b> That the fingerprint moves when each of those fields
 * moves, that it does not move when nothing moves, and that two claims cannot produce the
 * same material by accident.</p>
 */
class ReportJourneyFingerprintTest {

    /** The workspace the claims below belong to. */
    private static final String WORKSPACE = "photosynthesis";

    /** The identifier of the claim under comparison. */
    private static final String CLAIM_ID = "clm-fingerprint";

    /** The concept the claim belongs to. */
    private static final String CONCEPT_ID = "concept-photosynthesis";

    @Test
    @DisplayName("the fingerprint moves when the claim's concept, scope, context or status moves")
    void theFingerprintCoversEveryFieldThatMovesTheAnswer() {
        String baseline = WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "a scope",
                "a context", ClaimStatus.SUPPORTED, 1)));

        assertThat(WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "a scope", "a context",
                ClaimStatus.SUPPORTED, 1))))
                .as("the same workspace twice has the same fingerprint")
                .isEqualTo(baseline);
        assertThat(WorkspaceFingerprint.of(snapshot(claim("another-concept", "a scope",
                "a context", ClaimStatus.SUPPORTED, 1))))
                .as("a claim that moved to another concept moves the fingerprint")
                .isNotEqualTo(baseline);
        assertThat(WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "another scope",
                "a context", ClaimStatus.SUPPORTED, 1))))
                .as("a claim whose scope moved moves the fingerprint")
                .isNotEqualTo(baseline);
        assertThat(WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "a scope",
                "another context", ClaimStatus.SUPPORTED, 1))))
                .as("a claim whose context moved moves the fingerprint")
                .isNotEqualTo(baseline);
        assertThat(WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "a scope", "a context",
                ClaimStatus.REFUTED, 1))))
                .as("a claim whose status moved moves the fingerprint")
                .isNotEqualTo(baseline);
        assertThat(WorkspaceFingerprint.of(snapshot(claim(CONCEPT_ID, "a scope", "a context",
                ClaimStatus.SUPPORTED, 2))))
                .as("a claim whose version moved moves the fingerprint")
                .isNotEqualTo(baseline);
    }

    @Test
    @DisplayName("two claims cannot produce the same material by accident")
    void twoClaimsCannotCollide() {
        // the separators are part of the material: a claim whose fields are shifted must
        // not be able to stand for another one
        String first = WorkspaceFingerprint.of(snapshot(
                claim(CONCEPT_ID, "a|scope", "a context", ClaimStatus.SUPPORTED, 1)));
        String second = WorkspaceFingerprint.of(snapshot(
                claim("a", "scope", "a context", ClaimStatus.SUPPORTED, 1)));
        assertThat(first).isNotEqualTo(second);
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    /** A workspace holding one claim. */
    private static KnowledgeSnapshot snapshot(Claim claim) {
        return new KnowledgeSnapshot(WORKSPACE, WORKSPACE, List.of(), List.of(), List.of(claim),
                List.of(), List.of(), List.of(), List.of(), "2026-11-16T00:00:00Z");
    }

    /** One claim, with the fields the fingerprint covers set as the test needs them. */
    private static Claim claim(String conceptId, String scope, String context, ClaimStatus status,
            int version) {
        return new Claim(CLAIM_ID, WORKSPACE, conceptId, "water is split during photosynthesis",
                scope, context, status, 0.9, version, "source-1", "test", "test", null, null,
                "2026-11-16T00:00:00Z", "2026-11-16T00:00:00Z");
    }
}
