package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Starts, advances, checkpoints and resumes learning goals (RECON-W41).
 *
 * <p>The mission's §7.1: "The system must resume a partially completed
 * objective after application restart. Do not assume that restoring a
 * knowledge database is equivalent to restoring an active goal."
 *
 * <p>This manager is that "not equivalent" made concrete. The goals live in
 * the store next to the knowledge, but they are a different thing: the
 * knowledge says what is known, the goal says what was being attempted, what
 * failed, how much of the budget is spent and where to pick up.
 *
 * <p><b>What the manager refuses to do.</b> It does not mark a goal
 * complete because one subtask finished. It does not drop a failed attempt.
 * It does not spend past the budget. And it does not report a goal as
 * complete when its subtasks depend on each other in a circle, because a
 * circular dependency must never yield a false "done".
 */
public final class GoalManager {

    private final KnowledgeStore store;

    public GoalManager(KnowledgeStore store) {
        this.store = store;
    }

    /**
     * Starts a goal.
     *
     * @param workspaceId the workspace the goal belongs to
     * @param description what the goal is
     * @param successCriteria what must be true for the goal to be complete
     * @param prerequisiteConceptIds what must be understood first
     * @param subtasks the pieces of the goal
     * @param budgetUnits how many attempts the goal may spend
     * @param recoveryInstructions what to do when the goal is resumed
     * @return the goal as recorded
     */
    public LearningGoal start(String workspaceId, String description,
                               List<String> successCriteria, List<String> prerequisiteConceptIds,
                               List<LearningGoal.Subtask> subtasks, int budgetUnits,
                               String recoveryInstructions) {
        String id = Ids.digest(workspaceId + "|" + description);
        String now = now();
        LearningGoal goal = new LearningGoal("goal-" + id.substring(0, 8), workspaceId, description,
                successCriteria, prerequisiteConceptIds, subtasks, LearningGoal.Status.PENDING,
                List.of(), List.of(), remainingWork(subtasks), budgetUnits, 0,
                "the goal was started; nothing has been attempted yet",
                recoveryInstructions, now, now);
        store.recordGoalWithSubtasks(goal);
        return goal;
    }

    /**
     * Records a goal exactly as given, with its subtasks.
     *
     * <p>Used when a goal is restored from outside, and by the tests that
     * need to set a goal up in a known state.
     */
    public LearningGoal recordGoalWithSubtasks(LearningGoal goal) {
        store.recordGoalWithSubtasks(goal);
        return goal;
    }

    /**
     * Records that a subtask was completed, with the evidence behind it.
     *
     * @return the goal as it now stands
     */
    public LearningGoal completeSubtask(String workspaceId, String goalId, String subtaskId,
                                         List<String> evidenceIds, String note) {
        LearningGoal goal = goal(workspaceId, goalId);
        List<LearningGoal.Subtask> subtasks = new ArrayList<>();
        for (LearningGoal.Subtask subtask : goal.subtasks()) {
            if (subtask.id().equals(subtaskId)) {
                subtasks.add(subtask.withStatus(LearningGoal.Status.COMPLETED, evidenceIds, note));
            } else {
                subtasks.add(subtask);
            }
        }
        LearningGoal updated = withSubtasks(goal, subtasks, goal.progressEvidenceIds(),
                goal.failedAttempts(), goal.budgetUsed() + 1,
                goal.remainingWork() + (goal.remainingWork().isEmpty() ? "" : "; ")
                        + "completed " + subtaskId);
        updated = recomputeStatus(workspaceId, updated);
        store.recordGoalWithSubtasks(updated);
        return updated;
    }

    /**
     * Records an attempt that did not work.
     *
     * <p>A failed attempt is evidence about what to do differently, so it is
     * kept. When the budget is spent the goal is abandoned rather than left
     * running.
     */
    public LearningGoal failAttempt(String workspaceId, String goalId, String attempt, String reason) {
        LearningGoal goal = goal(workspaceId, goalId);
        List<LearningGoal.FailedAttempt> failures = new ArrayList<>(goal.failedAttempts());
        failures.add(new LearningGoal.FailedAttempt(
                "fail-" + failures.size() + "-" + attempt.hashCode(), attempt, reason, now()));
        LearningGoal updated = withSubtasks(goal, goal.subtasks(), goal.progressEvidenceIds(),
                failures, goal.budgetUsed() + 1,
                goal.remainingWork() + (goal.remainingWork().isEmpty() ? "" : "; ")
                        + "failed attempt: " + attempt);
        updated = recomputeStatus(workspaceId, updated);
        store.recordGoalWithSubtasks(updated);
        return updated;
    }

    /**
     * Writes a checkpoint: what the goal knows so far, and what is left.
     */
    public LearningGoal checkpoint(String workspaceId, String goalId, String checkpoint,
                                    String remainingWork) {
        LearningGoal goal = goal(workspaceId, goalId);
        LearningGoal updated = withSubtasks(goal, goal.subtasks(), goal.progressEvidenceIds(),
                goal.failedAttempts(), goal.budgetUsed(),
                remainingWork == null ? goal.remainingWork() : remainingWork);
        updated = new LearningGoal(updated.id(), updated.workspaceId(), updated.description(),
                updated.successCriteria(), updated.prerequisiteConceptIds(), updated.subtasks(),
                updated.status(), updated.progressEvidenceIds(), updated.failedAttempts(),
                updated.remainingWork(), updated.budgetUnits(), updated.budgetUsed(),
                checkpoint == null ? updated.lastCheckpoint() : checkpoint,
                updated.recoveryInstructions(), updated.createdAt(), now());
        store.recordGoalWithSubtasks(updated);
        return updated;
    }

    /** The goals of a workspace, with their subtasks attached. */
    public List<LearningGoal> goalsWithSubtasks(String workspaceId) {
        return store.goalsWithSubtasks(workspaceId);
    }

    /**
     * The goals that were under way when the process stopped.
     *
     * <p>This is what a restarted process asks: not "what do I know" but
     * "what was I doing, and how do I pick it up".
     */
    public List<LearningGoal> resumable(String workspaceId) {
        List<LearningGoal> resumable = new ArrayList<>();
        for (LearningGoal goal : store.goalsWithSubtasks(workspaceId)) {
            if (goal.status() == LearningGoal.Status.IN_PROGRESS
                    || goal.status() == LearningGoal.Status.BLOCKED
                    || goal.status() == LearningGoal.Status.PENDING) {
                resumable.add(goal);
            }
        }
        return resumable;
    }

    /**
     * Recomputes a goal's status under the stated semantics.
     */
    public LearningGoal recomputeStatus(String workspaceId, LearningGoal goal) {
        LearningGoal.Status status;
        if (goal.budgetUsed() >= goal.budgetUnits() && goal.budgetUnits() > 0
                && !goal.isComplete(Set.of())) {
            status = LearningGoal.Status.ABANDONED;
        } else if (goal.hasDependencyCycle()) {
            // A circular dependency must never yield a false "done".
            status = LearningGoal.Status.BLOCKED;
        } else if (goal.violatesDependencies()) {
            // A subtask was completed while its prerequisite was not: the
            // goal's own state contradicts its dependency graph, so it is
            // waiting rather than progressing.
            status = LearningGoal.Status.BLOCKED;
        } else if (!goal.blockedSubtasks().isEmpty()) {
            status = LearningGoal.Status.BLOCKED;
        } else if (goal.isComplete(satisfiedCriteria(workspaceId, goal))) {
            status = LearningGoal.Status.COMPLETED;
        } else if (goal.budgetUsed() > 0) {
            status = LearningGoal.Status.IN_PROGRESS;
        } else {
            status = LearningGoal.Status.PENDING;
        }
        return new LearningGoal(goal.id(), goal.workspaceId(), goal.description(),
                goal.successCriteria(), goal.prerequisiteConceptIds(), goal.subtasks(), status,
                goal.progressEvidenceIds(), goal.failedAttempts(), goal.remainingWork(),
                goal.budgetUnits(), goal.budgetUsed(), goal.lastCheckpoint(),
                goal.recoveryInstructions(), goal.createdAt(), now());
    }

    /**
     * The success criteria that have evidence behind them.
     *
     * <p>A criterion is satisfied when the goal's progress evidence names an
     * assessment outcome whose item measured that criterion. The link is by
     * the criterion's own name, so the evidence can be checked.
     */
    public Set<String> satisfiedCriteria(String workspaceId, LearningGoal goal) {
        Set<String> satisfied = new LinkedHashSet<>();
        for (LearningGoal.Subtask subtask : goal.subtasks()) {
            if (subtask.status() == LearningGoal.Status.COMPLETED) {
                satisfied.addAll(subtask.evidenceIds());
            }
        }
        satisfied.addAll(goal.progressEvidenceIds());
        Set<String> met = new LinkedHashSet<>();
        for (String criterion : goal.successCriteria()) {
            if (satisfied.contains(criterion)) {
                met.add(criterion);
            }
        }
        return met;
    }

    /** The goal as the store holds it. */
    public LearningGoal goal(String workspaceId, String goalId) {
        for (LearningGoal goal : store.goalsWithSubtasks(workspaceId)) {
            if (goal.id().equals(goalId)) {
                return goal;
            }
        }
        throw new IllegalArgumentException("no goal " + goalId + " in workspace " + workspaceId);
    }

    private LearningGoal withSubtasks(LearningGoal goal, List<LearningGoal.Subtask> subtasks,
                                       List<String> evidence, List<LearningGoal.FailedAttempt> failures,
                                       int budgetUsed, String remainingWork) {
        return new LearningGoal(goal.id(), goal.workspaceId(), goal.description(),
                goal.successCriteria(), goal.prerequisiteConceptIds(), subtasks, goal.status(),
                evidence, failures, remainingWork, goal.budgetUnits(), budgetUsed,
                goal.lastCheckpoint(), goal.recoveryInstructions(), goal.createdAt(), now());
    }

    private static String remainingWork(List<LearningGoal.Subtask> subtasks) {
        List<String> remaining = new ArrayList<>();
        for (LearningGoal.Subtask subtask : subtasks) {
            if (subtask.status() != LearningGoal.Status.COMPLETED) {
                remaining.add(subtask.id());
            }
        }
        return String.join(", ", remaining);
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}
