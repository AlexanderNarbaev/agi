package io.matrix.pilot.minecraft;

import java.util.List;
import java.util.Random;

/**
 * Baseline Minecraft pilot: selects random actions.
 *
 * <p>Used as a control for comparison against learned/evolved pilots.
 * Action selection is deterministic for the same seed.
 */
public final class NaiveMinecraftPilot implements MinecraftPilot {

    /** Actions the naive pilot can perform. */
    private static final List<String> ACTIONS = List.of(
            "MOVE_FORWARD", "MOVE_BACKWARD", "MOVE_LEFT", "MOVE_RIGHT",
            "JUMP", "MINE", "PLACE", "IDLE"
    );

    private Random rng;
    private WorldConfig config;

    @Override
    public void initialize(WorldConfig config) {
        this.config = config;
        this.rng = new Random(config.seed());
    }

    @Override
    public ActionResult step(AgentState state, Observation obs) {
        double actionPick = rng.nextDouble();
        double dx = (rng.nextDouble() - 0.5) * 2.0;
        double dy = rng.nextDouble() < 0.2 ? 1.0 : 0.0; // 20% chance to jump
        double dz = (rng.nextDouble() - 0.5) * 2.0;

        String action;
        if (actionPick < 0.3) {
            action = "MOVE_FORWARD";
            dx = 0.0;
            dz = 1.0;
        } else if (actionPick < 0.5) {
            action = "MOVE_LEFT";
            dx = -1.0;
        } else if (actionPick < 0.6) {
            action = "MOVE_RIGHT";
            dx = 1.0;
        } else if (actionPick < 0.7) {
            action = "MOVE_BACKWARD";
            dx = 0.0;
            dz = -1.0;
        } else if (actionPick < 0.8) {
            action = "JUMP";
            dy = 1.0;
        } else if (actionPick < 0.9) {
            action = "MINE";
        } else if (actionPick < 0.95) {
            action = "PLACE";
        } else {
            action = "IDLE";
        }

        return new ActionResult(action, dx, dy, dz, null);
    }

    @Override
    public double fitness(EpisodeHistory history) {
        return history.blocksPlaced()
                + history.itemsCollected()
                + history.distanceTraveled() / 100.0
                - history.deaths() * 5.0;
    }

    /** Random seed currently in use. */
    public long seed() {
        return config != null ? config.seed() : 0L;
    }
}
