package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the knowledge store's persistence contract.
 *
 * <p>The mission's acceptance criterion is not "a save method exists" but
 * "the state comes back". These tests therefore do the real thing: write,
 * close the process's handle on the database, open a NEW store against the
 * same file, and check that the knowledge — its provenance, its evidence
 * links, its representation levels and its revision history — is still there.
 */
class KnowledgeStoreTest {

    @TempDir
    Path dir;

    // ── the fixture ──────────────────────────────────────────────────────────

    private static KnowledgeSnapshot demoSnapshot() {
        String ws = "demo";
        String conceptId = Ids.concept(ws, "Photosynthesis");
        String advancedId = Ids.concept(ws, "Photosynthesis advanced");
        String sourceId = Ids.source(ws, SourceKind.AUTHORITATIVE_REFERENCE, "urn:isbn:978-0-00");
        String claimId = Ids.claim(ws, conceptId, "Plants use light energy to convert carbon dioxide and water into sugars.");
        String evidenceId = Ids.evidence(ws, claimId, sourceId);
        String introId = Ids.representation(ws, conceptId, AbstractionLevel.INTRODUCTORY, "Plants make sugars from light, water and CO2.");
        String mechId = Ids.representation(ws, conceptId, AbstractionLevel.MECHANISTIC, "Photosystem II splits water; the electron transport chain builds a proton gradient.");
        String revisionId = Ids.revision(ws, conceptId, RevisionKind.REFINEMENT, "Added the mechanistic account.");
        return new KnowledgeSnapshot(ws, "Demo workspace",
                List.of(new Concept(conceptId, ws, "Photosynthesis",
                        "Conversion of light energy into chemical energy.", "biology", "2026-10-31T00:00:00Z"),
                        new Concept(advancedId, ws, "Photosynthesis advanced",
                                "Mechanistic detail.", "biology", "2026-10-31T00:00:00Z")),
                List.of(new Source(sourceId, ws, SourceKind.AUTHORITATIVE_REFERENCE,
                        "A textbook", "urn:isbn:978-0-00", 90, 70, "Plants photosynthesise.", "2026-10-31T00:00:00Z")),
                List.of(new Claim(claimId, ws, conceptId,
                        "Plants use light energy to convert carbon dioxide and water into sugars.",
                        "In green plants, in light", "general biology", ClaimStatus.VERIFIED, 0.95, 1,
                        sourceId, "2026-10-31T00:00:00Z", "2026-10-31T00:00:00Z")),
                List.of(new Evidence(evidenceId, ws, claimId, sourceId, EvidenceRelation.SUPPORTS,
                        "Plants photosynthesise.", 90, "2026-10-31T00:00:00Z")),
                List.of(new Representation(introId, ws, conceptId, AbstractionLevel.INTRODUCTORY,
                                "beginner", "Plants make sugars from light, water and CO2.",
                                "introductory", List.of("plants photosynthesise"),
                                List.of("light energy is converted"), List.of("the Calvin cycle"),
                                List.of("only in green plants"), List.of(), null, 0.9,
                                "2026-10-31T00:00:00Z"),
                        new Representation(mechId, ws, conceptId, AbstractionLevel.MECHANISTIC,
                                "advanced", "Photosystem II splits water; the ETC builds a proton gradient.",
                                "mechanistic", List.of("light hits photosystem II"),
                                List.of("water oxidation"), List.of("the introductory simplification"),
                                List.of("requires light"), List.of(advancedId), introId, 0.8,
                                "2026-10-31T00:00:00Z")),
                List.of(new Prerequisite(ws, advancedId, conceptId, true, "the mechanism builds on the basics")),
                List.of(new KnowledgeRevision(revisionId, ws, "concept", conceptId, RevisionKind.REFINEMENT,
                                "Added the mechanistic account.",
                                List.of("water oxidation"), List.of("plants photosynthesise"),
                                List.of("the introductory simplification omits the proton gradient"),
                                List.of("why respiration is needed"), List.of(evidenceId),
                                "2026-10-31T00:00:00Z")),
                "2026-10-31T00:00:00Z");
    }

    // ── the contract ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("knowledge survives a restart with its provenance intact")
    void knowledgeSurvivesARestart() {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(demoSnapshot());
        }

        // A NEW store against the same file: this is the "stop the process,
        // start a new one" case, not a re-read of an open handle.
        try (KnowledgeStore reopened = new KnowledgeStore(db)) {
            KnowledgeSnapshot restored = reopened.exportWorkspace("demo");

            assertThat(restored.concepts()).hasSize(2);
            assertThat(restored.claims()).hasSize(1);
            assertThat(restored.sources()).hasSize(1);
            assertThat(restored.evidence()).hasSize(1);
            assertThat(restored.representations()).hasSize(2);
            assertThat(restored.prerequisites()).hasSize(1);
            assertThat(restored.revisions()).hasSize(1);

            Claim claim = restored.claims().get(0);
            assertThat(claim.status()).isEqualTo(ClaimStatus.VERIFIED);
            assertThat(claim.confidence()).isEqualTo(0.95);
            assertThat(claim.introducedBySourceId()).isNotBlank();

            // The evidence link still points at a source that is present.
            Evidence evidence = restored.evidence().get(0);
            assertThat(restored.sources()).extracting(Source::id).contains(evidence.sourceId());
            assertThat(restored.claims()).extracting(Claim::id).contains(evidence.claimId());

            // The refinement chain survived.
            Representation mechanistic = restored.representations().stream()
                    .filter(r -> r.level() == AbstractionLevel.MECHANISTIC).findFirst().orElseThrow();
            assertThat(mechanistic.refinesRepresentationId()).isNotBlank();
            assertThat(restored.representations()).extracting(Representation::id)
                    .contains(mechanistic.refinesRepresentationId());
            assertThat(mechanistic.deliberatelyOmitted()).contains("the introductory simplification");
        }
    }

    @Test
    @DisplayName("importing the same pack twice does not duplicate anything")
    void duplicateIngestionIsIdempotent() {
        Path db = dir.resolve("knowledge.sqlite");
        KnowledgeSnapshot snapshot = demoSnapshot();

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(snapshot);
            store.importWorkspace(snapshot);
            store.importWorkspace(snapshot);
        }

        try (KnowledgeStore reopened = new KnowledgeStore(db)) {
            KnowledgeSnapshot restored = reopened.exportWorkspace("demo");
            assertThat(restored.concepts()).hasSize(2);
            assertThat(restored.claims()).hasSize(1);
            assertThat(restored.evidence()).hasSize(1);
            assertThat(restored.representations()).hasSize(2);
            assertThat(restored.revisions()).hasSize(1);
        }
    }

    @Test
    @DisplayName("an invalid import leaves the store untouched")
    void anInvalidImportIsAtomic() {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(demoSnapshot());

            // A claim that claims to be VERIFIED but carries no evidence.
            String ws = "demo";
            String orphanClaim = Ids.claim(ws, "c-photosynthesis-x", "An unsupported assertion.");
            KnowledgeSnapshot broken = new KnowledgeSnapshot(ws, "Demo workspace",
                    demoSnapshot().concepts(),
                    demoSnapshot().sources(),
                    List.of(new Claim(orphanClaim, ws, demoSnapshot().concepts().get(0).id(),
                            "An unsupported assertion.", null, null, ClaimStatus.VERIFIED, 0.9, 1,
                            null, "2026-10-31T00:00:00Z", "2026-10-31T00:00:00Z")),
                    List.of(), demoSnapshot().representations(), demoSnapshot().prerequisites(),
                    demoSnapshot().revisions(), "2026-10-31T00:00:00Z");

            assertThatThrownBy(() -> store.importWorkspace(broken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no evidence");

            // The previously imported knowledge is still there, unchanged.
            KnowledgeSnapshot restored = store.exportWorkspace("demo");
            assertThat(restored.claims()).hasSize(1);
            assertThat(restored.claims().get(0).proposition())
                    .contains("Plants use light energy");
        }
    }

    @Test
    @DisplayName("a claim may not assert a status without evidence")
    void aClaimWithoutEvidenceIsRejected() {
        Path db = dir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot snapshot = demoSnapshot();
            List<Claim> claims = List.of(new Claim(
                    "cl-orphan", "demo", snapshot.concepts().get(0).id(), "Unsupported.", null, null,
                    ClaimStatus.SUPPORTED, 0.5, 1, null, "t", "t"));
            KnowledgeSnapshot broken = new KnowledgeSnapshot("demo", "Demo",
                    snapshot.concepts(), snapshot.sources(), claims, List.of(),
                    snapshot.representations(), snapshot.prerequisites(), snapshot.revisions(), "t");

            assertThatThrownBy(() -> store.importWorkspace(broken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("carries no evidence");
        }
    }

    @Test
    @DisplayName("a prerequisite cycle is rejected")
    void aPrerequisiteCycleIsRejected() {
        Path db = dir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot snapshot = demoSnapshot();
            List<Prerequisite> cyclic = List.of(
                    new Prerequisite("demo", snapshot.concepts().get(0).id(),
                            snapshot.concepts().get(1).id(), true, "a"),
                    new Prerequisite("demo", snapshot.concepts().get(1).id(),
                            snapshot.concepts().get(0).id(), true, "b"));
            KnowledgeSnapshot broken = new KnowledgeSnapshot("demo", "Demo",
                    snapshot.concepts(), snapshot.sources(), snapshot.claims(), snapshot.evidence(),
                    snapshot.representations(), cyclic, snapshot.revisions(), "t");

            assertThatThrownBy(() -> store.importWorkspace(broken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cycle");
        }
    }

    @Test
    @DisplayName("evidence must reference a claim and a source that exist")
    void danglingEvidenceIsRejected() {
        Path db = dir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            KnowledgeSnapshot snapshot = demoSnapshot();
            List<Evidence> dangling = List.of(new Evidence("e-dangling", "demo", "cl-missing",
                    snapshot.sources().get(0).id(), EvidenceRelation.SUPPORTS, "x", 50, "t"));
            KnowledgeSnapshot broken = new KnowledgeSnapshot("demo", "Demo",
                    snapshot.concepts(), snapshot.sources(), snapshot.claims(), dangling,
                    snapshot.representations(), snapshot.prerequisites(), snapshot.revisions(), "t");

            assertThatThrownBy(() -> store.importWorkspace(broken))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown claim");
        }
    }

    @Test
    @DisplayName("the store records its schema version and creates the file")
    void theStoreCreatesItsSchema() throws Exception {
        Path db = dir.resolve("nested").resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            assertThat(db).exists();
            assertThat(store.workspaces()).isEmpty();
        }
    }

    @Test
    @DisplayName("a backup can be restored by copying the file")
    void aBackupCanBeRestored() throws Exception {
        Path db = dir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(demoSnapshot());
        }

        Path backup = dir.resolve("backup.sqlite");
        Files.copy(db, backup);

        Path restoredDb = dir.resolve("restored.sqlite");
        Files.copy(backup, restoredDb);

        try (KnowledgeStore restored = new KnowledgeStore(restoredDb)) {
            assertThat(restored.exportWorkspace("demo").claims()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a second workspace does not disturb the first")
    void workspacesAreIsolated() {
        Path db = dir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            store.importWorkspace(demoSnapshot());
            store.importWorkspace(KnowledgeSnapshot.empty("other", "Other workspace"));

            assertThat(store.workspaces()).containsExactlyInAnyOrder("demo", "other");
            assertThat(store.exportWorkspace("demo").claims()).hasSize(1);
            assertThat(store.exportWorkspace("other").claims()).isEmpty();
        }
    }
}
