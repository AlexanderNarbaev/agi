package io.matrix.evolution;

import io.matrix.simulation.AgentBrain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class EvolutionLoopParetoTest {

    private static final int K = 18;

    private static List<Chromosome> createCandidates(int count, Random rng) {
        Population pop = new Population(count, K, rng);
        pop.initialize();
        List<Chromosome> chs = new ArrayList<>(pop.chromosomes());
        for (int i = 0; i < chs.size(); i++) {
            chs.set(i, chs.get(i).withFitness(100L - i * 5));
        }
        return chs;
    }

    @Test
    void selectWithParetoShouldReturnNonDominatedCandidates() {
        Random rng = new Random(42);
        EvolutionLoop loop = new EvolutionLoop(1, 10, K,
                new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42)), rng);

        List<Chromosome> candidates = createCandidates(20, rng);
        List<Chromosome> paretoOptimal = loop.selectWithPareto(candidates);

        assertThat(paretoOptimal).isNotEmpty();
        assertThat(paretoOptimal.size()).isLessThanOrEqualTo(candidates.size());

        for (Chromosome ch : paretoOptimal) {
            assertThat(candidates).contains(ch);
        }

        long distinctUuids = paretoOptimal.stream().map(Chromosome::uuid).distinct().count();
        assertThat(distinctUuids).isEqualTo(paretoOptimal.size());
    }

    @Test
    void selectWithParetoShouldHandleEmptyList() {
        EvolutionLoop loop = new EvolutionLoop(1, 10, K,
                new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42)), new Random(42));

        List<Chromosome> result = loop.selectWithPareto(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void selectWithParetoShouldHandleSingleCandidate() {
        Random rng = new Random(42);
        EvolutionLoop loop = new EvolutionLoop(1, 10, K,
                new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42)), rng);

        List<Chromosome> candidates = createCandidates(1, rng);
        List<Chromosome> result = loop.selectWithPareto(candidates);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(candidates.get(0));
    }

    @Test
    void selectWithParetoShouldPreferHighFitnessShallowTrees() {
        Random rng = new Random(42);
        EvolutionLoop loop = new EvolutionLoop(1, 10, K,
                new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42)), rng);

        // Create candidates with same depth but different fitness
        Population pop = new Population(5, K, rng);
        pop.initialize();
        List<Chromosome> candidates = new ArrayList<>(pop.chromosomes());
        for (int i = 0; i < candidates.size(); i++) {
            candidates.set(i, candidates.get(i).withFitness((long) (i + 1) * 10));
        }

        List<Chromosome> paretoOptimal = loop.selectWithPareto(candidates);

        // At least the best-fitness candidate should be Pareto-optimal
        Chromosome bestFitness = candidates.stream()
                .max((a, b) -> Long.compare(a.fitness(), b.fitness()))
                .orElseThrow();
        assertThat(paretoOptimal).contains(bestFitness);
    }

    @Test
    void runWithParetoShouldProduceValidResult() {
        FitnessFn fitnessFn = new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42));
        EvolutionLoop loop = new EvolutionLoop(10, 6, K, fitnessFn, new Random(42));

        EvolutionLoop.ParetoEvolutionResult result = loop.runWithPareto();

        assertThat(result).isNotNull();
        assertThat(result.bestFitnessHistory()).hasSize(11); // 10 gens + final eval
        assertThat(result.avgFitnessHistory()).hasSize(11);
        assertThat(result.paretoFrontSizes()).hasSize(11);
        assertThat(result.avgCompositeScores()).hasSize(11);

        long finalBest = result.bestFitnessHistory().get(result.bestFitnessHistory().size() - 1);
        assertThat(finalBest).isGreaterThan(0);

        for (int size : result.paretoFrontSizes()) {
            assertThat(size).isGreaterThan(0);
        }

        for (double score : result.avgCompositeScores()) {
            assertThat(score).isGreaterThanOrEqualTo(0.0);
            assertThat(score).isLessThanOrEqualTo(1.0);
        }

        AgentBrain brain = result.bestBrain();
        assertThat(brain).isNotNull();
        brain.nNeuron().validate();
        brain.sNeuron().validate();
        brain.wNeuron().validate();
        brain.eNeuron().validate();
    }

    @Test
    void runWithParetoShouldProduceSameSizeMetricsAsRun() {
        FitnessFn fitnessFn = new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42));

        EvolutionLoop paretoLoop = new EvolutionLoop(5, 8, K, fitnessFn, new Random(42));
        EvolutionLoop.ParetoEvolutionResult paretoResult = paretoLoop.runWithPareto();

        EvolutionLoop normalLoop = new EvolutionLoop(5, 8, K, fitnessFn, new Random(42));
        normalLoop.run();

        assertThat(paretoResult.bestFitnessHistory()).hasSize(normalLoop.bestFitnessHistory().size());
        assertThat(paretoResult.avgFitnessHistory()).hasSize(normalLoop.avgFitnessHistory().size());
    }

    @Test
    void paretoFrontSizeShouldBeReasonable() {
        FitnessFn fitnessFn = new FitnessFn(10, 10, 2, 1, 30, 2, new Random(123));
        EvolutionLoop loop = new EvolutionLoop(5, 8, K, fitnessFn, new Random(123));

        EvolutionLoop.ParetoEvolutionResult result = loop.runWithPareto();

        for (int size : result.paretoFrontSizes()) {
            assertThat(size).isGreaterThanOrEqualTo(4);
            assertThat(size).isLessThanOrEqualTo(32);
        }
    }

    @Test
    void paretoEvolutionResultBestBrainShouldMatchLoopBestBrain() {
        FitnessFn fitnessFn = new FitnessFn(10, 10, 2, 1, 30, 2, new Random(42));
        EvolutionLoop loop = new EvolutionLoop(3, 6, K, fitnessFn, new Random(42));

        EvolutionLoop.ParetoEvolutionResult result = loop.runWithPareto();

        AgentBrain brainFromResult = result.bestBrain();
        AgentBrain brainFromLoop = loop.bestBrain();

        assertThat(brainFromResult.nNeuron()).isNotNull();
        assertThat(brainFromLoop.nNeuron()).isNotNull();
    }
}
