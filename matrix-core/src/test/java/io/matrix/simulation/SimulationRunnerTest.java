package io.matrix.simulation;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.DecisionTree.Leaf;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.matrix.simulation.Direction.N;
import static org.assertj.core.api.Assertions.assertThat;

class SimulationRunnerTest {

    @Test
    void runnerShouldExecuteStepsUntilDeath() {
        World world = new World(10, 10, 5, 3, new Random(42));
        AgentBody body = new AgentBody(new Position(5, 5), 10);
        AgentBrain brain = new AgentBrain(
                new Leaf(true), new Leaf(false), new Leaf(false), new Leaf(false));

        SimulationRunner runner = new SimulationRunner(world, body, brain, 200, new Random(99));
        SimulationResult result = runner.run();

        assertThat(result.steps()).isGreaterThan(0);
        assertThat(result.foodCollected()).isGreaterThanOrEqualTo(0);
        assertThat(result.wallCollisions()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void runnerShouldStopAtMaxSteps() {
        World world = new World(10, 10, 0, 1, new Random(42));
        AgentBody body = new AgentBody(new Position(5, 5), 1000);
        AgentBrain brain = new AgentBrain(
                new Leaf(false), new Leaf(false), new Leaf(false), new Leaf(false));

        SimulationRunner runner = new SimulationRunner(world, body, brain, 50, new Random(99));
        SimulationResult result = runner.run();

        assertThat(result.steps()).isEqualTo(50);
        assertThat(body.energy()).isGreaterThan(0);
    }

    @Test
    void runnerShouldCollectResourceWhenMovingOverIt() {
        World world = new World(10, 10, 0, 0, new Random(42));
        world.grid().setCell(new Position(5, 4), CellType.RESOURCE);
        AgentBody body = new AgentBody(new Position(5, 5), 100);
        AgentBrain brain = new AgentBrain(
                new Leaf(true), new Leaf(false), new Leaf(false), new Leaf(false));

        SimulationRunner runner = new SimulationRunner(world, body, brain, 10, new Random(99));
        SimulationResult result = runner.run();

        assertThat(result.foodCollected()).isGreaterThan(0);
    }
}
