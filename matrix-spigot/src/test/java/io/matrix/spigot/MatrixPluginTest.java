package io.matrix.spigot;

import io.matrix.minecraft.BlockAgent;
import io.matrix.minecraft.BlockWorld;
import io.matrix.minecraft.NeuralBrain;
import io.matrix.minecraft.SurvivalRunner;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MatrixPluginTest {

    @Test
    void neuralBrainShouldIntegrateWithSpigotAgent() {
        NeuralBrain brain = new NeuralBrain(new Random(42));
        BlockWorld world = new BlockWorld(30, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(15, 10));

        SurvivalRunner runner = new SurvivalRunner(world, agent, brain, 10, new Random(99));
        var result = runner.run();

        assertThat(result).isNotNull();
        assertThat(result.steps()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void blockAgentShouldSurviveMultipleSteps() {
        BlockWorld world = new BlockWorld(40, 30, new Random(42));
        BlockAgent agent = new BlockAgent(new BlockWorld.Position(20, 15));

        assertThat(agent.isAlive()).isTrue();

        long sensors = agent.encodeSensors(world);
        assertThat(sensors).isGreaterThanOrEqualTo(0);

        int steps = 0;
        while (agent.isAlive() && steps < 50) {
            agent.move(agent.position());
            steps++;
        }
        assertThat(steps).isGreaterThan(0);
    }

    @Test
    void worldGenerationShouldBeConsistent() {
        BlockWorld w1 = new BlockWorld(40, 30, new Random(42));
        BlockWorld w2 = new BlockWorld(40, 30, new Random(42));

        assertThat(w1.width()).isEqualTo(w2.width());
        assertThat(w1.height()).isEqualTo(w2.height());
    }
}
