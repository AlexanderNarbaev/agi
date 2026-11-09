package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.DocumentImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W51 §6 — the knowledge and its history survive a restart.
 *
 * <p>The W50 wave's demonstration showed a store that survives a restart
 * for the records it had, but the check was a manual one. This test is the
 * mechanical form of it: a claim, its evidence and the revision that
 * superseded it are written, the store is CLOSED, the database is opened
 * again by a NEW process-equivalent, and the three are read back.
 *
 * <p>The point is the restart: a store that holds everything in memory and
 * writes nothing would pass a test that never closed it.
 */
class EpistemicDurabilityTest {

    /** A document whose sentences become claims. */
    private static final String LESSON = """
            Photosynthesis is the process by which green plants use light to convert carbon dioxide
            and water into sugar and oxygen.

            The light-dependent reactions happen in the thylakoid membrane. They produce ATP and NADPH.
            """;

    @Test
    @DisplayName("the claims, their evidence and the revision that superseded one survive a restart")
    void theClaimsTheirEvidenceAndTheRevisionSurviveARestart(@TempDir Path dir) throws IOException {
        Path database = dir.resolve("knowledge.sqlite");
        Path file = dir.resolve("lesson.txt");
        Files.writeString(file, LESSON, StandardCharsets.UTF_8);

        String workspace = "durability";
        String supersededClaimId;
        String revisionId;

        // ── the first process: write, revise, close ────────────────────────
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            new DocumentImporter(store).importDocument(workspace, file, null);
            List<Claim> claims = store.claims(workspace);
            assertThat(claims).isNotEmpty();
            Claim target = claims.get(0);

            // The refinement: the claim is superseded and the revision is
            // recorded.
            KnowledgeSnapshot snapshot = store.exportWorkspace(workspace);
            KnowledgeRefiner.Refinement refinement = new KnowledgeRefiner.Refinement(
                    target.conceptId(), target.id(),
                    "The light-dependent reactions happen in the thylakoid membrane.",
                    target.scope(), target.context(), ClaimStatus.SUPPORTED, 0.9,
                    SourceKind.PUBLICATION, "A second textbook", "urn:isbn:978-0-01",
                    "The light-dependent reactions happen in the thylakoid membrane.",
                    80, 80, RevisionKind.SUPERSESSION, "a correction", List.of(), List.of(),
                    List.of(), List.of(), null, null, null);
            KnowledgeChanges changes = new KnowledgeRefiner().refine(workspace, snapshot, refinement);
            store.applyChanges(workspace, changes, List.of());

            supersededClaimId = target.id();
            revisionId = changes.revisions().get(0).id();
        }

        // ── the second process: open and read ───────────────────────────────
        try (KnowledgeStore reopened = new KnowledgeStore(database)) {
            List<Claim> claims = reopened.claims(workspace);
            assertThat(claims).isNotEmpty();

            // The claim that was superseded is still there, marked
            // SUPERSEDED, with its version advanced.
            Claim superseded = claims.stream()
                    .filter(claim -> claim.id().equals(supersededClaimId))
                    .findFirst().orElseThrow();
            assertThat(superseded.status()).isEqualTo(ClaimStatus.SUPERSEDED);
            assertThat(superseded.version()).isEqualTo(2);
            assertThat(superseded.proposition()).isNotBlank();

            // Its evidence is still there, pointing at its own source.
            List<Evidence> evidence = reopened.evidence(workspace);
            assertThat(evidence).isNotEmpty();
            assertThat(evidence).allSatisfy(item -> {
                assertThat(item.excerpt()).isNotBlank();
                assertThat(item.sourceId()).isNotBlank();
            });
            assertThat(evidence).anySatisfy(item ->
                    assertThat(item.claimId()).isEqualTo(supersededClaimId));

            // And the revision that superseded it is still in the history.
            List<KnowledgeRevision> revisions = reopened.revisions(workspace);
            assertThat(revisions).anySatisfy(revision -> {
                assertThat(revision.id()).isEqualTo(revisionId);
                assertThat(revision.kind()).isEqualTo(RevisionKind.SUPERSESSION);
            });

            // The store still knows the workspace, and the counts are the
            // ones the status route reports.
            assertThat(reopened.workspaces()).contains(workspace);
            assertThat(reopened.claimStatusCounts(workspace)).containsKey("SUPERSEDED");
            assertThat(reopened.claimStatusCounts(workspace)).containsKey("SUPPORTED");
        }
    }

    @Test
    @DisplayName("the schema version does not move when the store is reopened")
    void theSchemaVersionDoesNotMoveWhenTheStoreIsReopened(@TempDir Path dir) {
        Path database = dir.resolve("knowledge.sqlite");
        int version;
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            version = store.schemaVersion();
        }
        try (KnowledgeStore reopened = new KnowledgeStore(database)) {
            assertThat(reopened.schemaVersion()).isEqualTo(version);
            assertThat(reopened.schemaVersion()).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
        }
    }
}
