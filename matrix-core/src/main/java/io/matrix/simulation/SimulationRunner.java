package io.matrix.simulation;

import java.util.Random;

/**
 * Runs an agent with a given brain in a world for up to maxSteps ticks.
 */
public class SimulationRunner {

    private static final int MOVE_ENERGY_COST = 1;
    private static final int WALL_PENALTY = 5;
    private static final int FOOD_ENERGY_GAIN = 20;

    private final World world;
    private final AgentBody body;
    private final AgentBrain brain;
    private final int maxSteps;
    private final Random rng;

    private int steps;
    private int foodCollected;
    private int wallCollisions;
    private final int initialEnergy;

    public SimulationRunner(World world, AgentBody body, AgentBrain brain, int maxSteps, Random rng) {
        this.world = world;
        this.body = body;
        this.brain = brain;
        this.maxSteps = maxSteps;
        this.rng = rng;
        this.initialEnergy = body.energy();
    }

    public SimulationResult run() {
        for (steps = 0; steps < maxSteps && body.isAlive(); steps++) {
            long sensors = body.sensors(world.grid());
            Direction action = brain.act(sensors);

            if (action == Direction.STAY) {
                body.consumeEnergy(MOVE_ENERGY_COST);
                continue;
            }

            Position nextPos = new Position(
                    body.position().x() + action.dx(),
                    body.position().y() + action.dy());

            if (world.isWall(nextPos)) {
                wallCollisions++;
                body.consumeEnergy(WALL_PENALTY);
            } else {
                body.move(action);
                body.consumeEnergy(MOVE_ENERGY_COST);
                if (world.isResource(body.position())) {
                    world.collectResource(body.position());
                    body.addEnergy(FOOD_ENERGY_GAIN);
                    foodCollected++;
                }
            }
        }
        return new SimulationResult(steps, foodCollected, wallCollisions,
                initialEnergy, body.energy());
    }
}
