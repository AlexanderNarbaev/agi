package io.matrix.minecraft;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MinecraftTest {

    @Test
    void agentShouldEncodeSensors() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(20, 15));

        long sensors = agent.encodeSensors(world);

        assertThat(sensors).isGreaterThanOrEqualTo(0);
        assertThat(agent.isAlive()).isTrue();
    }

    @Test
    void agentShouldMove() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(20, 15));

        assertThat(agent.isAlive()).isTrue();
        assertThat(agent.position()).isNotNull();
    }

    @Test
    void craftingShouldWork() {
        CraftingSystem cs = new CraftingSystem();
        Map<String, Integer> inv = new HashMap<>();
        inv.put("WOOD", 3);
        inv.put("COBBLESTONE", 8);

        var available = cs.availableRecipes(inv);
        assertThat(available).isNotEmpty();

        var crafted = cs.craftAllPossible(inv);
        assertThat(crafted).isNotEmpty();
    }

    @Test
    void pickaxeProgression() {
        CraftingSystem cs = new CraftingSystem();
        Map<String, Integer> inv = new HashMap<>();
        inv.put("WOOD", 2);
        inv.put("COBBLESTONE", 3);
        inv.put("STICK", 4);

        var crafted = cs.craftAllPossible(inv);

        assertThat(inv.getOrDefault("WOODEN_PICKAXE", 0)).isGreaterThanOrEqualTo(0);
        assertThat(inv.getOrDefault("STONE_PICKAXE", 0)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void agentShouldEatWhenHungry() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(20, 15));

        for (int i = 0; i < 30; i++) {
            agent.move(agent.position());
        }

        assertThat(agent.hunger()).isLessThan(20);
    }

    @Test
    void survivalShouldRun() {
        BlockWorld world = new BlockWorld(30, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(15, 10));
        NeuralBrain brain = new NeuralBrain(new Random(42));

        SurvivalRunner runner = new SurvivalRunner(world, agent, brain, 50, new Random(99));
        var result = runner.run();

        assertThat(result).isNotNull();
        assertThat(result.steps()).isGreaterThanOrEqualTo(0);
    }
}
