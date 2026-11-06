package io.matrix.cognitive;

import java.util.List;

/**
 * W44 — an internal task the system generated for itself.
 *
 * <p>The W44 directive §13 requires that a self-generated task say why it was initiated, which
 * higher-level goal it serves, its expected benefit, its cost, its completion condition, its
 * resource budget, its allowed actions and its stop condition — and that no autonomous task may
 * run indefinitely or reach outside its declared scope. This record is that declaration.</p>
 *
 * <p>It is deliberately NOT a {@code LearningGoal}. A learning goal is a curriculum promise
 * about a concept and is managed by {@code io.matrix.knowledge.GoalManager}; an internal task is
 * a bounded unit of work the system decided to do. Keeping them apart is what stops MATRIX from
 * growing a second, competing goal hierarchy.</p>
 *
 * <p>{@code allowedActions} is an ALLOW-LIST, not a description: an action that is not named
 * here is not authorised by this task. The kernel never executes an action that is outside the
 * list of the task that asked for it.</p>
 */
public record CognitiveTask(
        String id,
        String workspaceId,
        String origin,
        String reason,
        String servesGoal,
        String expectedBenefit,
        String costEstimate,
        String completionCondition,
        String stopCondition,
        List<String> allowedActions,
        long budgetWallClockMillis,
        String status,
        String createdAt,
        String completedAt) {

    /** The task has been created and has not been finished. */
    public static final String STATUS_OPEN = "OPEN";
    /** The task reached its completion condition. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** The task hit its stop condition without completing. */
    public static final String STATUS_STOPPED = "STOPPED";

    public CognitiveTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("task id must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("task must state why it was initiated");
        }
        if (completionCondition == null || completionCondition.isBlank()) {
            throw new IllegalArgumentException("task must state its completion condition");
        }
        if (stopCondition == null || stopCondition.isBlank()) {
            throw new IllegalArgumentException("task must state its stop condition");
        }
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    /** True when the task has not finished. */
    public boolean open() {
        return STATUS_OPEN.equals(status);
    }

    @Override
    public String toString() {
        return "CognitiveTask[" + id + " " + status + " reason=" + reason + "]";
    }
}
