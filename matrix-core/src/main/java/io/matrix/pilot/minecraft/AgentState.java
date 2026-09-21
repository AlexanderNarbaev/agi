package io.matrix.pilot.minecraft;

/**
 * Agent state at the beginning of a step.
 *
 * @param x       current X coordinate
 * @param y       current Y coordinate (elevation)
 * @param z       current Z coordinate
 * @param energy  remaining action energy (decremented per action)
 * @param tick    current game tick
 */
public record AgentState(
        double x,
        double y,
        double z,
        int energy,
        long tick) {
}
