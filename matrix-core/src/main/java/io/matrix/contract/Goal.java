package io.matrix.contract;

import java.util.List;

/**
 * W51 — what the system is trying to achieve.
 *
 * <p><b>A goal is a promise about a state of affairs.</b> It names a success condition that
 * can be checked, a priority that decides what wins when two goals compete, and a budget that
 * bounds what may be spent pursuing it. A goal with no checkable success condition is a wish,
 * and the W51 requirement — that the system can prioritise "by goal priority" and that it can
 * report whether it succeeded — cannot be met by one.</p>
 *
 * <p><b>The success condition is declared before the pursuit.</b> That is what makes the
 * outcome a measurement rather than a story: an experiment whose success is decided after the
 * fact is not an experiment, and the same is true of a goal.</p>
 *
 * <p><b>The priority is a number in {@code [0,1]} and it is comparable.</b> Two goals with the
 * same priority are not ordered by their id; the tie-break is declared where the goals are
 * compared, not here, so that a reader of a goal record is never misled by an implicit
 * order.</p>
 *
 * @param id              the identity of the goal
 * @param description     what the goal is
 * @param successCriteria the checkable conditions under which the goal is met
 * @param priority        the priority of the goal, in {@code [0,1]}
 * @param budgetUnits     the budget the goal may spend
 * @param status          the state of the goal
 * @param parentGoalId    the goal this one serves, or {@code null}
 * @param taskIds         the tasks the goal was broken into
 * @param createdAt       when the goal was created
 * @param completedAt     when the goal was met, or {@code null}
 */
public record Goal(
        String id,
        String description,
        List<String> successCriteria,
        double priority,
        int budgetUnits,
        Status status,
        String parentGoalId,
        List<String> taskIds,
        String createdAt,
        String completedAt) {

    /** The state of a goal. */
    public enum Status {
        /** The goal is being pursued. */
        ACTIVE,
        /** The goal was met. */
        ACHIEVED,
        /** The goal was given up. */
        ABANDONED,
        /** The goal is waiting for something outside the system. */
        SUSPENDED
    }

    public Goal {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("goal id must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("goal description must not be blank");
        }
        successCriteria = successCriteria == null ? List.of() : List.copyOf(successCriteria);
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        if (Double.isNaN(priority) || priority < 0.0 || priority > 1.0) {
            throw new IllegalArgumentException("goal priority must be within [0,1]");
        }
        if (budgetUnits < 0) {
            throw new IllegalArgumentException("goal budget must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("goal status must not be null");
        }
    }

    /** True when the goal names a checkable success condition. */
    public boolean checkable() {
        return !successCriteria.isEmpty();
    }

    /** True when the goal is still being pursued. */
    public boolean active() {
        return status == Status.ACTIVE;
    }

    /**
     * The achieved form of this goal.
     *
     * @param at when the goal was met
     * @return the achieved goal
     */
    public Goal achievedAt(String at) {
        return new Goal(id, description, successCriteria, priority, budgetUnits, Status.ACHIEVED,
                parentGoalId, taskIds, createdAt, at);
    }

    @Override
    public String toString() {
        return "Goal[" + id + " " + status + " priority=" + priority + "]";
    }
}
