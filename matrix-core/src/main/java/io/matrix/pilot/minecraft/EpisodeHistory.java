package io.matrix.pilot.minecraft;

/**
 * Summary of one training/evaluation episode.
 *
 * @param totalSteps        total game ticks elapsed
 * @param blocksPlaced      number of blocks placed by the agent
 * @param itemsCollected    number of items collected by the agent
 * @param distanceTraveled  total distance traveled (block units)
 * @param deaths            number of deaths
 */
public record EpisodeHistory(
        long totalSteps,
        int blocksPlaced,
        int itemsCollected,
        double distanceTraveled,
        int deaths) {
}
