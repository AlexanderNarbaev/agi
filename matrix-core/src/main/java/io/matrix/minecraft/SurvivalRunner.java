package io.matrix.minecraft;

import java.util.Random;

/**
 * Survival simulation — runs an agent with a MATRIX neural brain
 * in the Minecraft-like block world.
 *
 * <p>Tracks: survival steps, blocks mined, items crafted, tool progression.
 */
public class SurvivalRunner {

    private final BlockWorld world;
    private final BlockAgent agent;
    private final NeuralBrain brain;
    private final CraftingSystem crafting;
    private final int maxSteps;
    private final Random rng;

    public SurvivalRunner(BlockWorld world, BlockAgent agent, NeuralBrain brain,
                           int maxSteps, Random rng) {
        this.world = world;
        this.agent = agent;
        this.brain = brain;
        this.crafting = new CraftingSystem();
        this.maxSteps = maxSteps;
        this.rng = rng;
    }

    public SurvivalResult run() {
        int step;
        for (step = 0; step < maxSteps && agent.isAlive(); step++) {
            long sensors = agent.encodeSensors(world);
            BlockAgent.Action action = brain.act(sensors);

            execute(action);
        }

        boolean survived = agent.isAlive();
        return new SurvivalResult(step, agent.stepsSurvived(),
                agent.blocksMined(), agent.itemsCrafted(),
                agent.toolTier(), survived, agent.health(), agent.hunger(),
                agent.inventory());
    }

    private void execute(BlockAgent.Action action) {
        switch (action) {
            case BlockAgent.Action.Move move -> {
                int dx = 0, dy = 0;
                switch (move.direction()) {
                    case N -> dy = -1;
                    case S -> dy = 1;
                    case W -> dx = -1;
                    case E -> dx = 1;
                    case STAY -> { return; }
                }
                BlockWorld.Position target = agent.position().add(dx, dy);
                if (!world.isSolid(target.x(), target.y())) {
                    agent.move(target);
                }
            }
            case BlockAgent.Action.Mine mine -> {
                BlockWorld.Position target = findAdjacentBlock();
                if (target != null) {
                    agent.mineBlock(world, target);
                }
            }
            case BlockAgent.Action.Craft craft -> {
                crafting.craftAllPossible(inventoryMap());
                checkToolUpgrades();
            }
            case BlockAgent.Action.Eat eat -> agent.eat();
        }
    }

    private BlockWorld.Position findAdjacentBlock() {
        int px = agent.position().x();
        int py = agent.position().y();
        int[][] dirs = {{0,-1},{0,1},{-1,0},{1,0}};
        for (int[] d : dirs) {
            int tx = px + d[0], ty = py + d[1];
            BlockType block = world.get(tx, ty);
            if (block.mineable() && agent.toolTier().ordinal() >= block.minTool().ordinal()) {
                return new BlockWorld.Position(tx, ty);
            }
        }
        return null;
    }

    private java.util.Map<String, Integer> inventoryMap() {
        return new java.util.HashMap<>(agent.inventory());
    }

    private void checkToolUpgrades() {
        var inv = inventoryMap();
        if (inv.getOrDefault("DIAMOND_PICKAXE", 0) > 0) agent.upgradeTool("DIAMOND_PICKAXE");
        else if (inv.getOrDefault("IRON_PICKAXE", 0) > 0) agent.upgradeTool("IRON_PICKAXE");
        else if (inv.getOrDefault("STONE_PICKAXE", 0) > 0) agent.upgradeTool("STONE_PICKAXE");
        else if (inv.getOrDefault("WOODEN_PICKAXE", 0) > 0) agent.upgradeTool("WOODEN_PICKAXE");
    }

    public record SurvivalResult(
            int steps, int survived, int blocksMined, int itemsCrafted,
            BlockType.ToolTier finalTool, boolean alive, int health, int hunger,
            java.util.Map<String, Integer> inventory
    ) {
        public double score() {
            return blocksMined * 1.0 + itemsCrafted * 3.0
                    + (alive ? survived * 0.5 : 0) + finalTool.ordinal() * 5.0;
        }

        @Override
        public String toString() {
            return String.format("steps=%d alive=%s blocks=%d crafted=%d tool=%s health=%d hunger=%d score=%.1f",
                    steps, alive, blocksMined, itemsCrafted, finalTool, health, hunger, score());
        }
    }
}
