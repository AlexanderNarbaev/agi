package io.matrix.simulation;

/**
 * Result of a single simulation run.
 */
public record SimulationResult(
        int steps,
        int foodCollected,
        int wallCollisions,
        int initialEnergy,
        int finalEnergy
) {
    public long rawScore() {
        return (long) foodCollected * 100 - (long) wallCollisions * 10 + finalEnergy;
    }
}
