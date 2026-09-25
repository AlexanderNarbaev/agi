package io.matrix.federation.liquid;

import java.util.*;

/**
 * W585 — GridWorld Bridge for agent learning.
 *
 * Connects AutonomyEngine to a simple grid world environment.
 * Agent takes actions based on BIR/MCTS decisions.
 * Metric: survival time, task completion speed, energy efficiency.
 */
public final class GridWorldBridge {

    /**
     * Grid world cell types.
     */
    public enum CellType {
        EMPTY, WALL, FOOD, HAZARD, GOAL
    }

    /**
     * Agent actions.
     */
    public enum Action {
        NORTH, SOUTH, EAST, WEST, STAY
    }

    /**
     * Grid world state.
     */
    public static final class GridState {
        private final int width;
        private final int height;
        private final CellType[][] grid;
        private int agentX;
        private int agentY;
        private double energy;
        private int steps;
        private boolean done;

        public GridState(int width, int height) {
            this.width = width;
            this.height = height;
            this.grid = new CellType[height][width];
            this.energy = 1.0;
            this.steps = 0;
            this.done = false;

            // Initialize empty
            for (int y = 0; y < height; y++) {
                Arrays.fill(grid[y], CellType.EMPTY);
            }
        }

        public void setCell(int x, int y, CellType type) {
            if (x >= 0 && x < width && y >= 0 && y < height) {
                grid[y][x] = type;
            }
        }

        public void setAgent(int x, int y) {
            this.agentX = x;
            this.agentY = y;
        }

        public CellType getCell(int x, int y) {
            if (x < 0 || x >= width || y < 0 || y >= height) return CellType.WALL;
            return grid[y][x];
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getAgentX() { return agentX; }
        public int getAgentY() { return agentY; }
        public double getEnergy() { return energy; }
        public int getSteps() { return steps; }
        public boolean isDone() { return done; }
    }

    /**
     * Step result.
     */
    public record StepResult(
            double reward,
            boolean done,
            String observation,
            Map<String, Double> metrics
    ) {}

    private final GridState state;
    private final Random rng;

    public GridWorldBridge(int width, int height, long seed) {
        this.state = new GridState(width, height);
        this.rng = new Random(seed);
    }

    /**
     * Take a step in the grid world.
     */
    public StepResult step(Action action) {
        if (state.done) {
            return new StepResult(0, true, "Game over", Map.of());
        }

        int newX = state.agentX;
        int newY = state.agentY;

        switch (action) {
            case NORTH -> newY--;
            case SOUTH -> newY++;
            case EAST -> newX++;
            case WEST -> newX--;
            case STAY -> { /* no movement */ }
        }

        // Check bounds
        CellType targetCell = state.getCell(newX, newY);
        if (targetCell == CellType.WALL) {
            newX = state.agentX;
            newY = state.agentY;
        }

        // Move agent
        state.agentX = newX;
        state.agentY = newY;
        state.steps++;
        state.energy -= 0.01; // Energy cost per step

        // Check cell effects
        double reward = -0.01; // Small negative reward per step
        String observation = "Moved to (" + newX + "," + newY + ")";

        switch (targetCell) {
            case FOOD -> {
                reward += 0.5;
                state.energy = Math.min(1.0, state.energy + 0.3);
                state.setCell(newX, newY, CellType.EMPTY);
                observation = "Found food!";
            }
            case HAZARD -> {
                reward -= 1.0;
                state.energy -= 0.2;
                observation = "Hit hazard!";
            }
            case GOAL -> {
                reward += 10.0;
                state.done = true;
                observation = "Reached goal!";
            }
            default -> { /* empty */ }
        }

        // Check death conditions
        if (state.energy <= 0) {
            state.done = true;
            reward -= 5.0;
            observation = "Ran out of energy!";
        }

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("energy", state.energy);
        metrics.put("steps", (double) state.steps);
        metrics.put("x", (double) state.agentX);
        metrics.put("y", (double) state.agentY);

        return new StepResult(reward, state.done, observation, metrics);
    }

    /**
     * Get available actions from current state.
     */
    public List<Action> getAvailableActions() {
        List<Action> actions = new ArrayList<>();
        actions.add(Action.STAY);

        if (state.getCell(state.agentX, state.agentY - 1) != CellType.WALL) actions.add(Action.NORTH);
        if (state.getCell(state.agentX, state.agentY + 1) != CellType.WALL) actions.add(Action.SOUTH);
        if (state.getCell(state.agentX + 1, state.agentY) != CellType.WALL) actions.add(Action.EAST);
        if (state.getCell(state.agentX - 1, state.agentY) != CellType.WALL) actions.add(Action.WEST);

        return actions;
    }

    /**
     * Get current state.
     */
    public GridState getState() {
        return state;
    }

    /**
     * Generate a random level.
     */
    public void generateRandomLevel(int wallCount, int foodCount, int hazardCount) {
        // Place walls
        for (int i = 0; i < wallCount; i++) {
            int x = rng.nextInt(state.getWidth());
            int y = rng.nextInt(state.getHeight());
            if (state.getCell(x, y) == CellType.EMPTY) {
                state.setCell(x, y, CellType.WALL);
            }
        }

        // Place food
        for (int i = 0; i < foodCount; i++) {
            int x = rng.nextInt(state.getWidth());
            int y = rng.nextInt(state.getHeight());
            if (state.getCell(x, y) == CellType.EMPTY) {
                state.setCell(x, y, CellType.FOOD);
            }
        }

        // Place hazards
        for (int i = 0; i < hazardCount; i++) {
            int x = rng.nextInt(state.getWidth());
            int y = rng.nextInt(state.getHeight());
            if (state.getCell(x, y) == CellType.EMPTY) {
                state.setCell(x, y, CellType.HAZARD);
            }
        }

        // Place goal
        int goalX = state.getWidth() - 2;
        int goalY = state.getHeight() - 2;
        state.setCell(goalX, goalY, CellType.GOAL);
    }
}
