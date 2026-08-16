package io.matrix.pilot.minecraft;

/**
 * Minecraft pilot interface — defines the contract for agents
 * operating in a Minecraft world via the MATRIX Spigot plugin.
 *
 * <p>L13 Pilot #1: agent in Minecraft world. The pilot receives
 * environment observations and produces actions. The fitness
 * function measures episode quality for evolutionary optimization.
 *
 * <p>Implementations must be deterministic (same seed → same actions).
 * Per CONSTITUTION VII.1: no LLM calls in runtime.
 *
 * @see NaiveMinecraftPilot baseline (random actions)
 */
public interface MinecraftPilot {

    /**
     * Initialize the pilot with world configuration.
     * Called once before any steps.
     */
    void initialize(WorldConfig config);

    /**
     * Produce the next action given current state and observation.
     *
     * @param state current agent state (position, energy, tick)
     * @param obs  current environmental observation
     * @return the action to execute this tick
     */
    ActionResult step(AgentState state, Observation obs);

    /**
     * Compute fitness for a completed episode.
     * Higher values = better performance.
     *
     * <p>Baseline formula: blocksPlaced + itemsCollected + distanceTraveled/100
     * minus penalty for deaths (5× per death). The EXP-005 goal is
     * −30% time to reach the same fitness level.
     */
    double fitness(EpisodeHistory history);
}
