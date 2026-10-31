package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the whole lifecycle, in the order a user performs it.
 *
 * <p>This is the acceptance test the owner directive describes: create a
 * workspace, import knowledge, ask a question, be assessed, review the
 * learning state, then CLOSE THE PROCESS and check that a NEW process finds
 * everything still there.
 *
 * <p>The restart is real. The first workbench is closed, its file handle is
 * released, and a second workbench is built over the same path — the
 * equivalent of stopping the application and starting it again.
 */
class KnowledgeWorkbenchTest {

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench() {
        return KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
    }

    @Test
    @DisplayName("the whole lifecycle survives a restart")
    void theLifecycleSurvivesARestart() {
        Path db = dir.resolve("workbench.sqlite");

        // ── the first process ────────────────────────────────────────────
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(workbench.hasWorkspace("photosynthesis")).isFalse();

            KnowledgeSnapshot loaded = workbench.loadBundledPackage("photosynthesis");
            assertThat(loaded.concepts()).hasSize(8);
            assertThat(workbench.hasWorkspace("photosynthesis")).isTrue();

            // Ask a question the workspace can answer.
            Answer answer = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(answer.answered()).isTrue();
            assertThat(answer.sources()).isNotEmpty();

            // Ask a question it cannot, and see the abstention.
            Answer unknown = workbench.ask("photosynthesis", "What is the boiling point of mercury?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(unknown.answered()).isFalse();

            // Be assessed: answer correctly, then wrongly.
            AssessmentOutcome good = workbench.assess("photosynthesis", "t-recall-1",
                    "Photosynthesis uses light, carbon dioxide and water to make sugars.",
                    0.8, "session-1");
            assertThat(good.correct()).isTrue();

            AssessmentOutcome bad = workbench.assess("photosynthesis", "e-recall-1",
                    "Plants are green.", 0.9, "session-1");
            assertThat(bad.correct()).isFalse();

            // The learning state is derived from what was recorded.
            List<LearningState> states = workbench.learningStates("photosynthesis");
            assertThat(states).isNotEmpty();
            assertThat(states).anyMatch(s -> s.evidenceCount() > 0);

            assertThat(workbench.outcomes("photosynthesis")).hasSize(2);
        }

        // ── the second process ───────────────────────────────────────────
        try (KnowledgeWorkbench restarted = KnowledgeWorkbench.at(db.toString())) {
            assertThat(restarted.hasWorkspace("photosynthesis")).isTrue();

            KnowledgeSnapshot recovered = restarted.workspace("photosynthesis");
            assertThat(recovered.concepts()).hasSize(8);
            assertThat(recovered.claims()).hasSize(17);
            assertThat(recovered.sources()).hasSize(4);
            assertThat(recovered.evidence()).isNotEmpty();
            assertThat(recovered.representations()).hasSize(13);
            assertThat(recovered.revisions()).hasSize(1);

            // The assessment history is there too.
            assertThat(restarted.outcomes("photosynthesis")).hasSize(2);
            assertThat(restarted.learningStates("photosynthesis"))
                    .anyMatch(s -> s.evidenceCount() == 2);

            // And the knowledge is still usable: the answer survives, because
            // it is read from the store rather than from a cache.
            Answer answer = restarted.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(answer.answered()).isTrue();
            assertThat(answer.statement()).isNotBlank();
        }
    }

    @Test
    @DisplayName("the storage status reports what is actually on disk")
    void theStatusReportsTheTruth() {
        Path db = dir.resolve("workbench.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            var before = workbench.status();
            assertThat(before.get("exists")).isEqualTo(false);
            assertThat(before.get("workspaces")).isEqualTo(List.of());

            workbench.loadBundledPackage("photosynthesis");

            var after = workbench.status();
            assertThat(after.get("exists")).isEqualTo(true);
            assertThat(after.get("schemaVersion")).isEqualTo(KnowledgeStore.SCHEMA_VERSION);
            assertThat((Long) after.get("sizeBytes")).isPositive();
            assertThat((List<String>) after.get("workspaces")).containsExactly("photosynthesis");
        }
    }

    @Test
    @DisplayName("a workspace can be reloaded without duplicating anything")
    void reloadingIsIdempotent() {
        try (KnowledgeWorkbench workbench = workbench()) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("photosynthesis");
            workbench.loadBundledPackage("photosynthesis");

            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");
            assertThat(snapshot.concepts()).hasSize(8);
            assertThat(snapshot.claims()).hasSize(17);
            assertThat(snapshot.representations()).hasSize(13);
        }
    }

    @Test
    @DisplayName("the learning state is derived from the evidence, not from the size of the store")
    void theLearningStateFollowsTheEvidence() {
        try (KnowledgeWorkbench workbench = workbench()) {
            workbench.loadBundledPackage("photosynthesis");

            // Before any assessment, no dimension has a state.
            assertThat(workbench.learningStates("photosynthesis")).isEmpty();

            workbench.assess("photosynthesis", "t-recall-1",
                    "Photosynthesis uses light, carbon dioxide and water to make sugars.", 0.8, "s1");

            List<LearningState> states = workbench.learningStates("photosynthesis");
            assertThat(states).isNotEmpty();
            assertThat(states).allMatch(s -> s.evidenceCount() > 0);
            // A single outcome is not competence.
            assertThat(states).noneMatch(s -> s.stage() == LearningStage.COMPETENCE);
        }
    }
}
