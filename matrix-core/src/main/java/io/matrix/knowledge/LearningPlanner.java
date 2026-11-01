package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses the next bounded learning action (RECON-W42.1).
 *
 * <p>The mission's §4.6 asks for a goal-directed planning subsystem that can
 * inspect the active goals, identify the missing prerequisites and the
 * knowledge gaps, estimate the cost, select an appropriate strategy, execute
 * a bounded action, observe the outcome, compare it with the expectation,
 * update the plan and save a recovery checkpoint.
 *
 * <p><b>What RECON-W41 had.</b> Nothing selected anything. The subtasks
 * arrived in the HTTP request body, the goal recorded what a human had
 * already done, and the pieces that would make a planner possible were
 * written and never called: `LearningGoal.remainingSubtasks()`,
 * `blockedSubtasks()`, `hasDependencyCycle()`, and
 * `KnowledgeGapDetector.missingPrerequisites()` — the one function that
 * could name a missing prerequisite had zero callers.
 *
 * <p><b>What this planner does, and what it does not.</b> It selects from
 * what the workspace already knows:
 *
 * <ol>
 *   <li>a subtask whose own prerequisites are met is <b>runnable</b> — that
 *       is the action;</li>
 *   <li>a subtask whose prerequisites are not met is <b>blocked</b>, and the
 *       action is to work on the missing dependency, not to force the
 *       subtask;</li>
 *   <li>a goal whose prerequisite <b>concepts</b> the workspace does not
 *       hold is <b>not ready</b>, and the action is to acquire the missing
 *       concept.</li>
 * </ol>
 *
 * <p>It proposes no new content. A subtask is a claim, and a claim needs
 * evidence — that is the acquisition pipeline's business, and until it
 * exists the honest answer is to name what is missing.
 *
 * <p><b>The cost estimate is checkable.</b> It is the number of prerequisite
 * concepts the workspace does not hold, plus the number of the subtask's own
 * unmet dependencies. A number that can be recomputed by reading the store
 * is worth more than a number that comes out of a heuristic.
 */
public final class LearningPlanner {

    /** The kind of action the planner selected. */
    public enum ActionKind {
        /** Work on this subtask: its dependencies are met. */
        WORK_ON_SUBTASK,
        /** The subtask is blocked by another subtask; work on that one. */
        UNBLOCK_SUBTASK,
        /** The goal needs a concept the workspace does not hold. */
        ACQUIRE_CONCEPT,
        /** The goal's budget is spent. */
        BUDGET_SPENT
    }

    /** One selected action. */
    public record NextAction(
            ActionKind kind,
            String goalId,
            String goalDescription,
            String subtaskId,
            String subtaskDescription,
            String action,
            String reason,
            List<String> missingConceptIds,
            int estimatedCost) {

        public NextAction {
            missingConceptIds = List.copyOf(
                    missingConceptIds == null ? List.of() : missingConceptIds);
        }

        /** A one-line description, for the UI. */
        public String describe() {
            return action + " — " + reason;
        }
    }

    /** The whole plan. */
    public record Plan(List<NextAction> actions, String summary) {
        public Plan {
            actions = List.copyOf(actions == null ? List.of() : actions);
        }

        /** The plan with nothing to do. */
        public static Plan empty(String summary) {
            return new Plan(List.of(), summary);
        }
    }

    private final KnowledgeSnapshot snapshot;
    private final LearningGoal goal;
    private final KnowledgeGapDetector gaps;

    public LearningPlanner(KnowledgeSnapshot snapshot, LearningGoal goal) {
        this.snapshot = snapshot;
        this.goal = goal;
        this.gaps = new KnowledgeGapDetector(snapshot);
    }

    /**
     * Selects the next action for one goal.
     *
     * @return the plan; empty when the goal has nothing left to do
     */
    public Plan plan() {
        if (goal == null) {
            return Plan.empty("no goal");
        }
        if (goal.status() == LearningGoal.Status.COMPLETED) {
            return Plan.empty("the goal is complete");
        }
        if (goal.status() == LearningGoal.Status.ABANDONED) {
            return Plan.empty("the goal was abandoned");
        }
        if (goal.budgetUsed() >= goal.budgetUnits() && goal.budgetUnits() > 0) {
            return new Plan(List.of(new NextAction(ActionKind.BUDGET_SPENT, goal.id(),
                    goal.description(), "", "", "stop working on this goal",
                    "the budget of " + goal.budgetUnits() + " attempt(s) is spent",
                    List.of(), 0)), "the goal's budget is spent");
        }
        if (goal.hasDependencyCycle()) {
            return Plan.empty("the goal's subtasks depend on each other in a circle, so no "
                    + "next action can be selected until the cycle is broken");
        }

        // The concepts the workspace does not hold that the goal needs.
        List<String> missingConcepts = new ArrayList<>();
        for (String conceptId : goal.prerequisiteConceptIds()) {
            if (gaps.missingPrerequisites(conceptId).isEmpty()
                    && !conceptExists(conceptId)) {
                missingConcepts.add(conceptId);
            }
        }
        if (!missingConcepts.isEmpty()) {
            return new Plan(List.of(new NextAction(ActionKind.ACQUIRE_CONCEPT, goal.id(),
                    goal.description(), "", "",
                    "acquire " + describeConcepts(missingConcepts),
                    "the goal's prerequisites include concept(s) this workspace does not hold",
                    missingConcepts, missingConcepts.size())),
                    "the goal is waiting for knowledge it does not have");
        }

        // The subtasks that are still open, and the ones that can run.
        List<LearningGoal.Subtask> open = goal.remainingSubtasks();
        if (open.isEmpty()) {
            return Plan.empty("every subtask is complete; the goal needs its criteria checked");
        }
        Set<String> complete = new LinkedHashSet<>();
        for (LearningGoal.Subtask subtask : goal.subtasks()) {
            if (subtask.status() == LearningGoal.Status.COMPLETED) {
                complete.add(subtask.id());
            }
        }
        for (LearningGoal.Subtask subtask : open) {
            List<String> unmet = subtask.prerequisiteSubtaskIds().stream()
                    .filter(dependency -> !complete.contains(dependency))
                    .toList();
            if (unmet.isEmpty()) {
                return new Plan(List.of(new NextAction(ActionKind.WORK_ON_SUBTASK, goal.id(),
                        goal.description(), subtask.id(), subtask.description(),
                        "work on \"" + subtask.description() + "\"",
                        "its dependencies are met", List.of(), 1)),
                        "one action is ready");
            }
        }

        // Nothing can run: every remaining subtask waits for something that
        // is not done. The action is to unblock the first blocked subtask,
        // naming what blocks it - and when the blocker is not even part of
        // the goal, saying so rather than pretending it can be completed.
        LearningGoal.Subtask blocked = goal.blockedSubtasks().isEmpty() ? open.get(0)
                : goal.blockedSubtasks().get(0);
        List<String> unmet = blocked.prerequisiteSubtaskIds().stream()
                .filter(dependency -> !complete.contains(dependency))
                .toList();
        Set<String> known = new LinkedHashSet<>();
        for (LearningGoal.Subtask subtask : goal.subtasks()) {
            known.add(subtask.id());
        }
        List<String> absent = unmet.stream().filter(dependency -> !known.contains(dependency)).toList();
        String action;
        String reason;
        if (!absent.isEmpty()) {
            action = "fix the goal: " + String.join(", ", absent) + " is not a subtask of this goal";
            reason = "the subtask waits for a dependency the goal does not define";
        } else {
            action = "complete " + String.join(", ", unmet) + " first";
            reason = "the subtask waits for "
                    + (unmet.size() == 1 ? "another subtask" : "other subtasks");
        }
        return new Plan(List.of(new NextAction(ActionKind.UNBLOCK_SUBTASK, goal.id(),
                goal.description(), blocked.id(), blocked.description(),
                action, reason, List.of(), Math.max(1, unmet.size()))),
                "the next action is blocked by another subtask");
    }

    private boolean conceptExists(String conceptId) {
        return snapshot.concepts().stream().anyMatch(concept -> concept.id().equals(conceptId));
    }

    private String describeConcepts(List<String> conceptIds) {
        List<String> names = new ArrayList<>();
        for (String conceptId : conceptIds) {
            String name = snapshot.concepts().stream()
                    .filter(concept -> concept.id().equals(conceptId))
                    .map(Concept::name).findFirst().orElse(conceptId);
            names.add(name);
        }
        return String.join(", ", names);
    }
}
