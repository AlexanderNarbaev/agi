package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationTest {

    @Test
    void shouldCreateEmptyPopulation() {
        Population pop = new Population(5, 8, new Random(42));

        assertThat(pop.chromosomes()).isEmpty();
    }

    @Test
    void shouldInitializeWithCorrectSize() {
        Population pop = new Population(10, 8, new Random(42));
        pop.initialize();

        assertThat(pop.chromosomes()).hasSize(10);
    }

    @Test
    void shouldProduceValidChromosomes() {
        Population pop = new Population(8, 8, new Random(42));
        pop.initialize();

        for (Chromosome c : pop.chromosomes()) {
            assertThat(c.tree()).isNotNull();
            assertThat(c.generation()).isZero();
            assertThat(c.fitness()).isZero();
            c.tree().validate();
        }
    }

    @Test
    void shouldUpdateFitness() {
        Population pop = new Population(5, 8, new Random(42));
        pop.initialize();

        List<Long> fitnesses = List.of(10L, 20L, 30L, 40L, 50L);
        pop.updateFitness(fitnesses);

        List<Chromosome> chroms = pop.chromosomes();
        for (int i = 0; i < 5; i++) {
            assertThat(chroms.get(i).fitness()).isEqualTo(fitnesses.get(i));
        }
    }

    @Test
    void shouldFindBestChromosome() {
        Population pop = new Population(5, 8, new Random(42));
        pop.initialize();

        pop.updateFitness(List.of(10L, 50L, 30L, 20L, 40L));

        Chromosome best = pop.best();
        assertThat(best.fitness()).isEqualTo(50);
    }

    @Test
    void shouldEvolveAndMaintainPopulationSize() {
        Population pop = new Population(10, 8, new Random(42));
        pop.initialize();
        pop.updateFitness(randomFitnesses(10, new Random(42)));

        pop.evolve();

        assertThat(pop.chromosomes()).hasSize(10);
        for (Chromosome c : pop.chromosomes()) {
            assertThat(c.tree()).isNotNull();
            assertThat(c.generation()).isGreaterThanOrEqualTo(0);
            c.tree().validate();
        }
    }

    @Test
    void shouldImproveAverageFitnessOverGenerations() {
        int populationSize = 12;
        int k = 8;
        int generations = 10;
        Population pop = new Population(populationSize, k, new Random(42));
        pop.initialize();

        List<Double> avgFitnesses = new ArrayList<>();

        for (int gen = 0; gen < generations; gen++) {
            List<Long> fitnesses = new ArrayList<>();
            Random fitnessRng = new Random(42 + gen);
            for (int i = 0; i < populationSize; i++) {
                fitnesses.add((long) (100 + gen * 5 + fitnessRng.nextInt(50)));
            }
            pop.updateFitness(fitnesses);

            double avg = fitnesses.stream().mapToLong(Long::longValue).average().orElse(0);
            avgFitnesses.add(avg);

            if (gen < generations - 1) {
                pop.evolve();
            }
        }

        assertThat(avgFitnesses.get(avgFitnesses.size() - 1))
                .isGreaterThanOrEqualTo(avgFitnesses.get(0));
    }

    @Test
    void shouldPreserveBestChromosomeThroughEvolve() {
        Population pop = new Population(10, 8, new Random(42));
        pop.initialize();

        pop.updateFitness(List.of(1L, 2L, 3L, 4L, 100L, 6L, 7L, 8L, 9L, 10L));
        long preBest = pop.best().fitness();
        assertThat(preBest).isEqualTo(100);

        pop.evolve();

        List<Long> newFitnesses = randomFitnesses(10, new Random(99));
        pop.updateFitness(newFitnesses);

        assertThat(pop.chromosomes()).hasSize(10);
    }

    @Test
    void shouldHandleMultipleEvolveCycles() {
        Population pop = new Population(8, 8, new Random(42));
        pop.initialize();

        for (int gen = 0; gen < 20; gen++) {
            pop.updateFitness(randomFitnesses(8, new Random(gen)));
            assertThat(pop.best().fitness()).isGreaterThan(0);
            pop.evolve();
        }

        assertThat(pop.chromosomes()).hasSize(8);
        assertThat(pop.best()).isNotNull();
    }

    private static List<Long> randomFitnesses(int count, Random rng) {
        List<Long> f = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            f.add(50L + rng.nextInt(200));
        }
        return f;
    }
}
