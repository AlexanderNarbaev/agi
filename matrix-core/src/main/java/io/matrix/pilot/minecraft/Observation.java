package io.matrix.pilot.minecraft;

import java.util.List;

/**
 * Observation of the environment at the current step.
 *
 * @param nearbyBlocks   list of block type names within view distance
 * @param nearbyEntities list of entity type names within view distance
 * @param health         agent health (0.0–20.0)
 * @param timeOfDay      game time (0–24000)
 * @param isNight        true if timeOfDay indicates night (13000–23000)
 */
public record Observation(
        List<String> nearbyBlocks,
        List<String> nearbyEntities,
        double health,
        long timeOfDay,
        boolean isNight) {
}
