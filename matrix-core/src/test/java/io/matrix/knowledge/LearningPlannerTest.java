package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.1 — something selects the next learning action.
 *
 * <p>The mission's §4.3: "Develop an actual planner rather than treating
 * persistence of goals as planning." §4.6: the planning subsystem inspects
 * the active goals, identifies the missing prerequisites and the knowledge
 * gaps, estimates the cost and selects an appropriate strategy.
 *
 * <p>RECON-W41 wrote every piece this needs — `remainingSubtasks()`,
 * `blockedSubtasks()`, `hasDependencyCycle()`, `missingPrerequisites()` —
 * and called none of them.
 */
class LearningPlannerTest {

    private LearningGoal goalWith(List<LearningGoal.Subtask> subtasks) {
        return new LearningGoal("g-1", "photosynthesis", "learn photosynthesis",
                List.of("the learner can explain it"), List.of(), subtasks,
                LearningGoal.Status.IN_PROGRESS, List.of(), List.of(), "read the chapter",
                5, 0, null, null, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");
    }

    private LearningGoal.Subtask subtask(String id, String description,
                                         LearningGoal.Status status, List<String> dependencies) {
        return new LearningGoal.Subtask(id, description, true, dependencies, status,
                List.of(), null);
    }

    @Test
    @DisplayName("the planner works on the subtask whose dependencies are met")
    void thePlannerWorksOnTheSubtaskWhoseDependenciesAreMet(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            LearningGoal goal = goalWith(List.of(
                    subtask("s-1", "read the chapter", LearningGoal.Status.PENDING, List.of()),
                    subtask("s-2", "answer the questions", LearningGoal.Status.PENDING,
                            List.of("s-1"))));

            LearningPlanner.Plan plan = new LearningPlanner(workbench.workspace("photosynthesis"),
                    goal).plan();

            assertThat(plan.actions()).hasSize(1);
            LearningPlanner.NextAction action = plan.actions().get(0);
            assertThat(action.kind()).isEqualTo(LearningPlanner.ActionKind.WORK_ON_SUBTASK);
            assertThat(action.subtaskId()).isEqualTo("s-1");
            assertThat(action.action()).contains("read the chapter");
            assertThat(action.reason()).contains("dependencies are met");
            assertThat(action.estimatedCost()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the planner unblocks a subtask instead of forcing it")
    void thePlannerUnblocksASubtaskInsteadOfForcingIt(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // The subtask waits for a dependency the goal does not define,
            // so nothing can run - and the planner says exactly that instead
            // of forcing the subtask.
            LearningGoal goal = goalWith(List.of(
                    subtask("s-1", "answer the questions", LearningGoal.Status.PENDING,
                            List.of("s-missing"))));

            LearningPlanner.Plan plan = new LearningPlanner(workbench.workspace("photosynthesis"),
                    goal).plan();

            assertThat(plan.actions()).hasSize(1);
            LearningPlanner.NextAction action = plan.actions().get(0);
            assertThat(action.kind()).isEqualTo(LearningPlanner.ActionKind.UNBLOCK_SUBTASK);
            assertThat(action.action()).contains("s-missing");
            assertThat(action.reason()).contains("does not define");
            assertThat(action.estimatedCost()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("the planner asks for the concept the goal needs and the workspace does not hold")
    void thePlannerAsksForTheConceptTheGoalNeeds(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            LearningGoal goal = new LearningGoal("g-2", "photosynthesis", "learn photosynthesis",
                    List.of("the learner can explain it"),
                    List.of("c-a-concept-the-workspace-does-not-have"),
                    List.of(subtask("s-1", "read the chapter", LearningGoal.Status.PENDING,
                            List.of())),
                    LearningGoal.Status.IN_PROGRESS, List.of(), List.of(), "read the chapter",
                    5, 0, null, null, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");

            LearningPlanner.Plan plan = new LearningPlanner(workbench.workspace("photosynthesis"),
                    goal).plan();

            assertThat(plan.actions()).hasSize(1);
            LearningPlanner.NextAction action = plan.actions().get(0);
            assertThat(action.kind()).isEqualTo(LearningPlanner.ActionKind.ACQUIRE_CONCEPT);
            assertThat(action.missingConceptIds())
                    .containsExactly("c-a-concept-the-workspace-does-not-have");
            assertThat(action.estimatedCost()).isEqualTo(1);
            assertThat(action.reason()).contains("does not hold");
        }
    }

    @Test
    @DisplayName("the planner stops when the goal's budget is spent")
    void thePlannerStopsWhenTheGoalsBudgetIsSpent(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            LearningGoal goal = new LearningGoal("g-3", "photosynthesis", "learn photosynthesis",
                    List.of("the learner can explain it"), List.of(),
                    List.of(subtask("s-1", "read the chapter", LearningGoal.Status.PENDING,
                            List.of())),
                    LearningGoal.Status.IN_PROGRESS, List.of(), List.of(), "read the chapter",
                    5, 5, null, null, "2026-11-01T00:00:00Z", "2026-11-01T00:00:00Z");

            LearningPlanner.Plan plan = new LearningPlanner(workbench.workspace("photosynthesis"),
                    goal).plan();

            assertThat(plan.actions()).hasSize(1);
            assertThat(plan.actions().get(0).kind())
                    .isEqualTo(LearningPlanner.ActionKind.BUDGET_SPENT);
        }
    }

    @Test
    @DisplayName("the planner refuses to plan a goal whose subtasks depend on each other")
    void thePlannerRefusesToPlanACircularGoal(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            LearningGoal goal = goalWith(List.of(
                    subtask("s-1", "first", LearningGoal.Status.PENDING, List.of("s-2")),
                    subtask("s-2", "second", LearningGoal.Status.PENDING, List.of("s-1"))));

            LearningPlanner.Plan plan = new LearningPlanner(workbench.workspace("photosynthesis"),
                    goal).plan();

            assertThat(plan.actions()).isEmpty();
            assertThat(plan.summary()).contains("circle");
        }
    }

    @Test
    @DisplayName("the workspace plans every active goal")
    void theWorkspacePlansEveryActiveGoal(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            workbench.startGoal("photosynthesis", "learn photosynthesis",
                    List.of("the learner can explain it"), List.of(),
                    List.of(new LearningGoal.Subtask("s-1", "read the chapter", true, List.of(),
                            LearningGoal.Status.PENDING, List.of(), null)),
                    5, null);

            List<LearningPlanner.Plan> plans = workbench.plan("photosynthesis");

            assertThat(plans).hasSize(1);
            assertThat(plans.get(0).actions()).hasSize(1);
            assertThat(plans.get(0).actions().get(0).kind())
                    .isEqualTo(LearningPlanner.ActionKind.WORK_ON_SUBTASK);
        }
    }
}
