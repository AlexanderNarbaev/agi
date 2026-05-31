package io.matrix.mediator;

import java.util.UUID;

/**
 * A goal generated when a driver exceeds its high threshold.
 *
 * <p>Ref: L4_Mediator.md §4.1
 */
public record Goal(
        UUID id,
        DriverType driver,
        String description,
        double priority,
        GoalStatus status
) {
    public static Goal create(DriverType driver, String description, double priority) {
        return new Goal(UUID.randomUUID(), driver, description, priority, GoalStatus.PENDING);
    }

    public Goal activate() {
        return new Goal(id, driver, description, priority, GoalStatus.ACTIVE);
    }

    public Goal satisfy() {
        return new Goal(id, driver, description, priority, GoalStatus.SATISFIED);
    }

    public Goal fail() {
        return new Goal(id, driver, description, priority, GoalStatus.FAILED);
    }
}
