package io.matrix.mediator;

import java.util.List;

/**
 * Snapshot of the system state used by {@link MetaGoalValidator} to simulate
 * the consequences of a proposed goal.
 *
 * <p>Ref: L4 §4.3 — critical-thinking axiom.
 *
 * @param resourceUsage      fraction of available resources in use, [0.0, 1.0]
 * @param activeGoals        goals currently being pursued
 * @param ethicalGuardActive whether the FROZEN ethical filter is engaged
 */
public record SystemState(
        double resourceUsage,
        List<Goal> activeGoals,
        boolean ethicalGuardActive
) {
    public SystemState {
        if (resourceUsage < 0.0 || resourceUsage > 1.0) {
            throw new IllegalArgumentException(
                    "resourceUsage must be in [0.0, 1.0], got: " + resourceUsage);
        }
        activeGoals = activeGoals == null ? List.of() : List.copyOf(activeGoals);
    }

    /**
     * Empty state with no active goals and idle resources.
     */
    public static SystemState idle() {
        return new SystemState(0.0, List.of(), true);
    }

    /**
     * A stressed state with high resource usage.
     */
    public static SystemState stressed() {
        return new SystemState(0.9, List.of(), true);
    }
}
