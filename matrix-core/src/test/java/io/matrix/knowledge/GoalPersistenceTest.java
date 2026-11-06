package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W41 — a goal survives the process that was pursuing it.
 *
 * <p>The mission's §7.1: "The system must resume a partially completed
 * objective after application restart. Do not assume that restoring a
 * knowledge database is equivalent to restoring an active goal."
 *
 * <p>And the mission's §12.6, the acceptance rule that matters:
 * <i>Create -&gt; Learn -&gt; Assess -&gt; Save -&gt; Force Stop -&gt; Restart -&gt;
 * Reload -&gt; Reassess</i>. These tests keep the goal part of it.
 */
class GoalPersistenceTest {

    private static LearningGoal.Subtask subtask(String id, String description, boolean required,
                                                List<String> prerequisites) {
        return new LearningGoal.Subtask(id, description, required, prerequisites,
                LearningGoal.Status.PENDING, List.of(), "");
    }

    private LearningGoal goal(String workspaceId, String description) {
        return new LearningGoal("goal-1", workspaceId, description,
                List.of("the learner can answer the held-out items"),
                List.of("c-photosynthesis"),
                List.of(
                        subtask("s-read", "Read the source material", true, List.of()),
                        subtask("s-assess", "Take the assessment", true, List.of("s-read")),
                        subtask("s-extra", "Write a summary", false, List.of())),
                LearningGoal.Status.PENDING, List.of(), List.of(), "s-read, s-assess, s-extra",
                10, 0, "nothing attempted", "reopen the workspace and continue with s-read",
                "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
    }

    @Test
    @DisplayName("a goal written before a restart is there after it")
    void aGoalWrittenBeforeARestartIsThereAfterIt(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));
        }

        // A new process opens the same database.
        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            List<LearningGoal> goals = manager.resumable("photosynthesis");

            assertThat(goals).hasSize(1);
            LearningGoal resumed = goals.get(0);
            assertThat(resumed.description()).isEqualTo("Learn photosynthesis");
            assertThat(resumed.subtasks()).hasSize(3);
            assertThat(resumed.successCriteria())
                    .containsExactly("the learner can answer the held-out items");
            assertThat(resumed.prerequisiteConceptIds()).containsExactly("c-photosynthesis");
            assertThat(resumed.recoveryInstructions()).contains("s-read");
            assertThat(resumed.remainingWork()).contains("s-assess");
        }
    }

    @Test
    @DisplayName("a subtask completed before the restart is still complete after it")
    void aSubtaskCompletedBeforeTheRestartIsStillCompleteAfterIt(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));
            manager.completeSubtask("photosynthesis", "goal-1", "s-read", List.of("e-1"), "read it");
        }

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            LearningGoal resumed = manager.goal("photosynthesis", "goal-1");

            LearningGoal.Subtask read = resumed.subtasks().stream()
                    .filter(candidate -> candidate.id().equals("s-read")).findFirst().orElseThrow();
            assertThat(read.status()).isEqualTo(LearningGoal.Status.COMPLETED);
            assertThat(read.evidenceIds()).containsExactly("e-1");
            // The budget was spent by the attempt.
            assertThat(resumed.budgetUsed()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a failed attempt is kept across a restart")
    void aFailedAttemptIsKeptAcrossARestart(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));
            manager.failAttempt("photosynthesis", "goal-1", "assess", "the key terms did not match");
        }

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            LearningGoal resumed = manager.goal("photosynthesis", "goal-1");

            assertThat(resumed.failedAttempts()).hasSize(1);
            assertThat(resumed.failedAttempts().get(0).reason())
                    .contains("the key terms did not match");
        }
    }

    @Test
    @DisplayName("a goal is not complete because one subtask finished")
    void aGoalIsNotCompleteBecauseOneSubtaskFinished(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));

            LearningGoal updated = manager.completeSubtask("photosynthesis", "goal-1", "s-read",
                    List.of("e-1"), "read it");

            assertThat(updated.status()).isEqualTo(LearningGoal.Status.IN_PROGRESS);
            assertThat(updated.status()).isNotEqualTo(LearningGoal.Status.COMPLETED);
        }
    }

    @Test
    @DisplayName("a subtask whose prerequisite is not done is blocked, not failed")
    void aSubtaskWhosePrerequisiteIsNotDoneIsBlocked(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));

            // s-assess depends on s-read, which has not been done.
            LearningGoal updated = manager.completeSubtask("photosynthesis", "goal-1", "s-assess",
                    List.of("e-2"), "skipped ahead");

            assertThat(updated.status()).isEqualTo(LearningGoal.Status.BLOCKED);
        }
    }

    @Test
    @DisplayName("an optional subtask does not block completion")
    void anOptionalSubtaskDoesNotBlockCompletion(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            manager.recordGoalWithSubtasks(goal("photosynthesis", "Learn photosynthesis"));
            manager.completeSubtask("photosynthesis", "goal-1", "s-read", List.of("e-1"), "read it");
            manager.completeSubtask("photosynthesis", "goal-1", "s-assess",
                    List.of("the learner can answer the held-out items"), "assessed");

            LearningGoal updated = manager.goal("photosynthesis", "goal-1");

            // The optional summary is still outstanding, but the goal is done.
            assertThat(updated.status()).isEqualTo(LearningGoal.Status.COMPLETED);
            assertThat(updated.remainingSubtasks()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a goal whose budget is spent is abandoned")
    void aGoalWhoseBudgetIsSpentIsAbandoned(@TempDir Path dir) {
        Path db = dir.resolve("knowledge.sqlite");

        try (KnowledgeStore store = new KnowledgeStore(db)) {
            GoalManager manager = new GoalManager(store);
            LearningGoal goal = new LearningGoal("goal-1", "photosynthesis", "Learn photosynthesis",
                    List.of("criterion"), List.of(),
                    List.of(subtask("s-read", "Read", true, List.of())),
                    LearningGoal.Status.PENDING, List.of(), List.of(), "s-read",
                    1, 0, "nothing attempted", "continue", "t", "t");
            manager.recordGoalWithSubtasks(goal);

            LearningGoal updated = manager.failAttempt("photosynthesis", "goal-1", "read",
                    "the source was unreadable");

            assertThat(updated.budgetUsed()).isEqualTo(1);
            assertThat(updated.status()).isEqualTo(LearningGoal.Status.ABANDONED);
        }
    }

    @Test
    @DisplayName("a circular dependency never yields a false completion")
    void aCircularDependencyNeverYieldsAFalseCompletion() {
        LearningGoal cyclic = new LearningGoal("goal-cycle", "photosynthesis", "A cyclic goal",
                List.of(), List.of(),
                List.of(
                        subtask("a", "A needs B", true, List.of("b")),
                        subtask("b", "B needs A", true, List.of("a"))),
                LearningGoal.Status.PENDING, List.of(), List.of(), "a, b",
                10, 0, "nothing attempted", "break the cycle", "t", "t");

        assertThat(cyclic.hasDependencyCycle()).isTrue();
        assertThat(cyclic.isComplete(java.util.Set.of())).isFalse();
    }
}
