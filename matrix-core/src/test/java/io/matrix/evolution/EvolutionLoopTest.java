package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.simulation.AgentBrain;
import io.matrix.simulation.Direction;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionLoopTest {

    @Test
    void evolutionLoopShouldImproveFitnessOverGenerations() {
        int generations = 20;
        int populationSize = 10;
        int k = 18;

        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 2, 50, 2, new Random(42));

        EvolutionLoop loop = new EvolutionLoop(generations, populationSize, k,
                fitnessFn, new Random(42));

        loop.run();

        assertThat(loop.bestFitnessHistory()).hasSize(generations + 1);

        long initialBest = loop.bestFitnessHistory().get(0);
        long maxOverall = loop.bestFitnessHistory().stream().mapToLong(Long::longValue).max().orElse(0);
        assertThat(maxOverall).isGreaterThanOrEqualTo(initialBest);
        assertThat(maxOverall).isGreaterThan(0);
    }

    @Test
    void evolutionLoopShouldProduceValidBrain() {
        FitnessFn fitnessFn = new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42));

        EvolutionLoop loop = new EvolutionLoop(10, 6, 18, fitnessFn, new Random(42));
        loop.run();

        AgentBrain brain = loop.bestBrain();
        assertThat(brain.nNeuron()).isNotNull();
        assertThat(brain.sNeuron()).isNotNull();
        assertThat(brain.wNeuron()).isNotNull();
        assertThat(brain.eNeuron()).isNotNull();

        brain.nNeuron().validate();
        brain.sNeuron().validate();
        brain.wNeuron().validate();
        brain.eNeuron().validate();
    }
}
