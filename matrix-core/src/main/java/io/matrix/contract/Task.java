package io.matrix.contract;

import java.util.List;

/**
 * W51 — a bounded unit of work the system decided to do.
 *
 * <p><b>This is the canonical form of the kernel's {@code io.matrix.cognitive.CognitiveTask},
 * not a replacement for it.</b> The kernel's record is the one the kernel uses; this one is
 * what crosses a boundary, what a plugin is told about, and what the reflection of a cycle
 * refers to. The kernel's record is adapted into this form.</p>
 *
 * <p><b>The allow-list is an allow-list.</b> {@link #allowedActions()} names the actions the
 * task permits. An action that is not named is not authorised by the task, and the runtime
 * refuses it. This is the property that stops an autonomous task from reaching outside its
 * declared scope.</p>
 *
 * <p><b>A task must be able to stop.</b> {@link #stopCondition()} is declared with the task,
 * not discovered later, so that "the task ran until it was interrupted" is a violation the
 * runtime can detect rather than a fact a reader has to infer.</p>
 *
 * @param id                 the identity of the task
 * @param goalId             the goal the task serves
 * @param reason             why the task was initiated
 * @param expectedBenefit    what the system expects to gain
 * @param costEstimate       what the task is expected to cost
 * @param completionCondition what must hold for the task to be complete
 * @param stopCondition      what must hold for the task to stop
 * @param allowedActions     the actions the task permits
 * @param budgetMillis       the wall-clock budget of the task
 * @param status             the state of the task
 * @param createdAt          when the task was created
 * @param completedAt        when the task ended, or {@code null}
 */
public record Task(
        String id,
        String goalId,
        String reason,
        String expectedBenefit,
        String costEstimate,
        String completionCondition,
        String stopCondition,
        List<String> allowedActions,
        long budgetMillis,
        Status status,
        String createdAt,
        String completedAt) {

    /** The state of a task. */
    public enum Status {
        /** The task is open. */
        OPEN,
        /** The task met its completion condition. */
        COMPLETED,
        /** The task hit its stop condition without completing. */
        STOPPED
    }

    public Task {
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
        if (budgetMillis < 0L) {
            throw new IllegalArgumentException("task budget must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("task status must not be null");
        }
    }

    /** True when the task has not finished. */
    public boolean open() {
        return status == Status.OPEN;
    }

    /**
     * Whether an action is permitted by this task.
     *
     * @param actionKind the kind of the action
     * @return true when the action is on the allow-list
     */
    public boolean permits(String actionKind) {
        return allowedActions.contains(actionKind);
    }

    @Override
    public String toString() {
        return "Task[" + id + " " + status + " goal=" + goalId + "]";
    }
}
