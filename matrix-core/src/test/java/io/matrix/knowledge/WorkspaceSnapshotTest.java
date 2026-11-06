package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W45.2 — the destructive reload fails closed and leaves a recoverable snapshot.
 *
 * <p>The owner's decision: a reload that replaces a workspace a user has worked in must say
 * so before it does it, and it must leave a snapshot of what it replaced. The defect it
 * answers is quiet and total: the reload deleted the workspace's rows and inserted the
 * package's rows in one transaction, and the contradiction history, the evidence and the
 * revisions went with it — with nothing in the response, in the log or in the database to
 * say that they had been there.</p>
 *
 * <p>These tests are the proof of the fix, and they check the three things that matter: a
 * refused load destroys nothing, a permitted load leaves a snapshot, and a restore brings
 * the content back — the conflicts included.</p>
 */
class WorkspaceSnapshotTest {

    /** The instant every record in this test carries. */
    private static final String INSTANT = "2026-11-04T00:00:00Z";

    /** The workspace the records live in. */
    private static final String WORKSPACE = "demo";

    @TempDir
    Path directory;

    // ── the fixture ──────────────────────────────────────────────────────────

    /**
     * A workspace with content of every kind the replacement erases.
     *
     * <p>Two claims, so that a recorded conflict can point at both of them, and one recorded
     * conflict: the contradiction history is what the owner named, so it is in the fixture
     * from the start.</p>
     */
    private static KnowledgeSnapshot demoSnapshot() {
        String conceptId = Ids.concept(WORKSPACE, "Photosynthesis");
        String sourceId = Ids.source(WORKSPACE, SourceKind.AUTHORITATIVE_REFERENCE, "urn:isbn:978-0-00");
        String claimId = Ids.claim(WORKSPACE, conceptId, "Plants use light to make sugars.");
        String otherClaimId = Ids.claim(WORKSPACE, conceptId, "Plants do not use light to make sugars.");
        String evidenceId = Ids.evidence(WORKSPACE, claimId, sourceId);
        String representationId = Ids.representation(WORKSPACE, conceptId,
                AbstractionLevel.INTRODUCTORY, "Plants make sugars from light, water and CO2.");
        String revisionId = Ids.revision(WORKSPACE, conceptId, RevisionKind.REFINEMENT,
                "Added the mechanistic account.");
        String derivationId = "der-1";
        return new KnowledgeSnapshot(WORKSPACE, "Demo workspace",
                List.of(new Concept(conceptId, WORKSPACE, "Photosynthesis",
                        "Conversion of light energy into chemical energy.", "biology", INSTANT)),
                List.of(new Source(sourceId, WORKSPACE, SourceKind.AUTHORITATIVE_REFERENCE,
                        "A textbook", "urn:isbn:978-0-00", 90, 70, "Plants photosynthesise.",
                        INSTANT, "hash-of-the-textbook-excerpt", null)),
                List.of(new Claim(claimId, WORKSPACE, conceptId,
                        "Plants use light to make sugars.", "In green plants", "general biology",
                        ClaimStatus.VERIFIED, 0.95, 1, sourceId, "transcribed from the excerpt",
                        "checked against the excerpt", ClaimWarrant.TESTIMONY,
                        DerivationStatus.NOT_DERIVED, INSTANT, INSTANT),
                        new Claim(otherClaimId, WORKSPACE, conceptId,
                                "Plants do not use light to make sugars.", "In green plants",
                                "general biology", ClaimStatus.CANDIDATE, 0.4, 1, sourceId,
                                "stated by a reader", "not validated", ClaimWarrant.TESTIMONY,
                                DerivationStatus.NOT_DERIVED, INSTANT, INSTANT)),
                List.of(new Evidence(evidenceId, WORKSPACE, claimId, sourceId,
                        EvidenceRelation.SUPPORTS, "Plants photosynthesise.", 90, INSTANT)),
                List.of(new Representation(representationId, WORKSPACE, conceptId,
                        AbstractionLevel.INTRODUCTORY, "beginner",
                        "Plants make sugars from light, water and CO2.", "introductory",
                        List.of("plants photosynthesise"), List.of("light energy is converted"),
                        List.of("the Calvin cycle"), List.of("only in green plants"), List.of(),
                        null, 0.9, INSTANT)),
                List.of(),
                List.of(new KnowledgeRevision(revisionId, WORKSPACE, "concept", conceptId,
                        RevisionKind.REFINEMENT, "Added the mechanistic account.",
                        List.of("water oxidation"), List.of("plants photosynthesise"),
                        List.of("the introductory simplification omits the proton gradient"),
                        List.of("why respiration is needed"), List.of(evidenceId), INSTANT)),
                List.of(new Derivation(derivationId, WORKSPACE, "rule-1", "the light rule",
                        Derivation.Connective.IMPLIES, claimId, List.of(otherClaimId),
                        DerivationStatus.DERIVED_VALID, INSTANT)),
                List.of(new Contradiction("con-1", WORKSPACE, claimId, otherClaimId,
                        Contradiction.Kind.OPPOSITE_POLARITY, "the contradiction detector",
                        INSTANT)),
                List.of(new LearningGoal("goal-1", WORKSPACE, "learn photosynthesis",
                        List.of("the learner can explain the light reaction"),
                        List.of(conceptId),
                        List.of(new LearningGoal.Subtask("sub-1", "read the chapter", true,
                                List.of(), LearningGoal.Status.PENDING, List.of(), null)),
                        LearningGoal.Status.IN_PROGRESS, List.of(), List.of(), "read the chapter",
                        3, 1, "the reading", "start again at the reading", INSTANT, INSTANT)),
                INSTANT);
    }

    /** A workspace with nothing in it. */
    private static KnowledgeSnapshot emptySnapshot() {
        return KnowledgeSnapshot.empty(WORKSPACE, "Demo workspace");
    }

    // ── the refusal ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a load into an empty workspace works without the flag")
    void aLoadIntoAnEmptyWorkspaceWorksWithoutTheFlag() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(demoSnapshot(), false, INSTANT);

            assertThat(store.exportWorkspace(WORKSPACE).concepts()).hasSize(1);
            assertThat(taken)
                    .withFailMessage("nothing was replaced, so there is nothing to snapshot")
                    .isNull();
        }
    }

    @Test
    @DisplayName("a load into a non-empty workspace without the flag is refused")
    void aLoadIntoANonEmptyWorkspaceWithoutTheFlagIsRefused() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());

            assertThatThrownBy(() -> store.importWorkspaceWithSnapshot(emptySnapshot(), false, INSTANT))
                    .isInstanceOf(WorkspaceReplacementRefused.class)
                    .withFailMessage("the refusal must say what the caller has to send")
                    .hasMessageContaining("replace=true");
        }
    }

    @Test
    @DisplayName("a refused load destroys nothing")
    void aRefusedLoadDestroysNothing() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            store.recordContradiction(new Contradiction("con-1", WORKSPACE, "clm-1", "clm-2",
                    Contradiction.Kind.OPPOSITE_POLARITY, "the contradiction detector", INSTANT));

            assertThatThrownBy(() -> store.importWorkspaceWithSnapshot(emptySnapshot(), false, INSTANT))
                    .isInstanceOf(WorkspaceReplacementRefused.class);

            // The conflict, the evidence and the revision are all still there.
            assertThat(store.contradictions(WORKSPACE)).hasSize(1);
            assertThat(store.evidence(WORKSPACE)).hasSize(1);
            assertThat(store.revisions(WORKSPACE)).hasSize(1);
            assertThat(store.concepts(WORKSPACE)).hasSize(1);
            assertThat(store.claims(WORKSPACE)).hasSize(2);
            assertThat(store.derivations(WORKSPACE)).hasSize(1);
        }
    }

    // ── the snapshot ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("a load with the flag takes a snapshot of what it replaced")
    void aLoadWithTheFlagTakesASnapshotOfWhatItReplaced() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());

            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);

            assertThat(taken).isNotNull();
            assertThat(taken.workspaceId()).isEqualTo(WORKSPACE);
            assertThat(taken.kind()).isEqualTo(WorkspaceSnapshot.Kind.LOAD);
            assertThat(taken.createdAt()).isEqualTo(INSTANT);
            assertThat(taken.conceptCount()).isEqualTo(1);
            assertThat(taken.claimCount()).isEqualTo(2);
            assertThat(taken.sourceCount()).isEqualTo(1);
            assertThat(taken.evidenceCount()).isEqualTo(1);
            assertThat(taken.representationCount()).isEqualTo(1);
            assertThat(taken.revisionCount()).isEqualTo(1);
            assertThat(taken.contradictionCount()).isEqualTo(1);
            assertThat(taken.derivationCount()).isEqualTo(1);
            assertThat(taken.goalCount()).isEqualTo(1);

            // And the workspace really was replaced.
            assertThat(store.exportWorkspace(WORKSPACE).concepts()).isEmpty();
        }
    }

    @Test
    @DisplayName("the snapshot is inspectable without being restored")
    void theSnapshotIsInspectableWithoutBeingRestored() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);

            List<WorkspaceSnapshot> snapshots = store.workspaceSnapshots(WORKSPACE);
            assertThat(snapshots).hasSize(1);
            assertThat(snapshots.get(0).conceptCount()).isEqualTo(1);
            assertThat(snapshots.get(0).contradictionCount()).isEqualTo(1);
            assertThat(snapshots.get(0).empty()).isFalse();

            // Inspecting did not restore anything.
            assertThat(store.exportWorkspace(WORKSPACE).concepts()).isEmpty();
        }
    }

    @Test
    @DisplayName("a snapshot exists for every destructive load")
    void aSnapshotExistsForEveryDestructiveLoad() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);
            store.importWorkspace(demoSnapshot());
            store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);

            assertThat(store.workspaceSnapshots(WORKSPACE)).hasSize(2);
            assertThat(store.workspaceSnapshots(WORKSPACE))
                    .extracting(WorkspaceSnapshot::id)
                    .as("the listing is newest first")
                    .containsExactly("snap-demo-2", "snap-demo-1");
        }
    }

    @Test
    @DisplayName("a load that replaces nothing leaves no snapshot")
    void aLoadThatReplacesNothingLeavesNoSnapshot() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspaceWithSnapshot(demoSnapshot(), false, INSTANT);

            assertThat(store.workspaceSnapshots(WORKSPACE)).isEmpty();
        }
    }

    // ── the restore ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a restore brings the workspace back, the conflicts included")
    void aRestoreBringsTheWorkspaceBack() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);
            assertThat(taken).isNotNull();

            store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            KnowledgeSnapshot restored = store.exportWorkspace(WORKSPACE);
            assertThat(restored.concepts()).hasSize(1);
            assertThat(restored.claims()).hasSize(2);
            assertThat(restored.sources()).hasSize(1);
            assertThat(restored.evidence()).hasSize(1);
            assertThat(restored.representations()).hasSize(1);
            assertThat(restored.revisions()).hasSize(1);
            assertThat(restored.derivations()).hasSize(1);
            assertThat(restored.contradictions())
                    .withFailMessage("the contradiction history must come back")
                    .hasSize(1);
            assertThat(restored.contradictions().get(0).id()).isEqualTo("con-1");
            assertThat(restored.goals())
                    .withFailMessage("the goals and their subtasks must come back")
                    .hasSize(1);
            assertThat(restored.goals().get(0).subtasks()).hasSize(1);
            assertThat(restored.goals().get(0).budgetUsed()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a restore snapshots what it replaced")
    void aRestoreSnapshotsWhatItReplaced() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);
            // The workspace holds something again, so the restore has something to replace.
            store.importWorkspace(demoSnapshot());

            WorkspaceSnapshot replaced = store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(replaced).isNotNull();
            assertThat(replaced.kind()).isEqualTo(WorkspaceSnapshot.Kind.RESTORE);
            assertThat(store.workspaceSnapshots(WORKSPACE)).hasSize(2);
        }
    }

    @Test
    @DisplayName("a restore into an empty workspace leaves no snapshot of nothing")
    void aRestoreIntoAnEmptyWorkspaceLeavesNoSnapshotOfNothing() {
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            store.importWorkspace(demoSnapshot());
            WorkspaceSnapshot taken = store.importWorkspaceWithSnapshot(emptySnapshot(), true, INSTANT);

            // The workspace is empty now, so the restore has nothing to replace.
            WorkspaceSnapshot replaced = store.restoreWorkspaceSnapshot(taken.id(), INSTANT);

            assertThat(replaced).isNull();
            assertThat(store.workspaceSnapshots(WORKSPACE)).hasSize(1);
        }
    }

    @Test
    @DisplayName("a snapshot of another workspace cannot be restored into this one")
    void aSnapshotOfAnotherWorkspaceCannotBeRestoredIntoThisOne() {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(
                directory.resolve("workbench.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            WorkspaceSnapshot taken = workbench.storeForValidation()
                    .takeWorkspaceSnapshot("photosynthesis", WorkspaceSnapshot.Kind.MANUAL, INSTANT);
            assertThat(taken).isNotNull();

            assertThatThrownBy(() -> workbench.restoreWorkspaceSnapshot("logic", taken.id()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── the concurrency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("two concurrent loads cannot interleave into a state without a snapshot")
    void twoConcurrentLoadsCannotInterleaveIntoAStateWithoutASnapshot() throws Exception {
        Path database = directory.resolve("concurrent.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            store.importWorkspace(demoSnapshot());
        }

        KnowledgeStore first = new KnowledgeStore(database);
        KnowledgeStore second = new KnowledgeStore(database);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> one = pool.submit(() -> {
                await(start);
                first.importWorkspaceWithSnapshot(demoSnapshot(), true, INSTANT);
                return null;
            });
            Future<?> two = pool.submit(() -> {
                await(start);
                second.importWorkspaceWithSnapshot(demoSnapshot(), true, INSTANT);
                return null;
            });
            start.countDown();
            one.get(60, TimeUnit.SECONDS);
            two.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            first.close();
            second.close();
        }

        try (KnowledgeStore store = new KnowledgeStore(database)) {
            assertThat(store.workspaceSnapshots(WORKSPACE))
                    .withFailMessage("every destructive load leaves a snapshot")
                    .hasSize(2);
            assertThat(store.workspaceSnapshots(WORKSPACE))
                    .extracting(WorkspaceSnapshot::id)
                    .containsExactlyInAnyOrder("snap-demo-1", "snap-demo-2");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Waits for the latch, so that two threads really do start together. */
    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("the test was interrupted", e);
        }
    }
}
