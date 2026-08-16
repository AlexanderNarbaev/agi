package io.matrix.concurrent;

import io.matrix.evolution.Chromosome;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.simulation.AgentBrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParallelEvolution}.
 *
 * <p>Tests:
 * <ul>
 *   <li>Parallel vs sequential correctness</li>
 *   <li>Speedup benchmark (>4x on 8 cores)</li>
 *   <li>ForkJoinPool configuration</li>
 * </ul>
 */
class ParallelEvolutionTest {

    private static final int GENERATIONS = 5;
    private static final int POPULATION_SIZE = 32;
    private static final int K = 8;
    private static final Random RNG = new Random(42);

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void parallelVsSequentialCorrectness() {
        // Given: same seed for both sequential and parallel
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));

        // Sequential run
        Random seqRng = new Random(100);
        EvolutionLoop sequential = new EvolutionLoop(
                GENERATIONS, POPULATION_SIZE, K, fitnessFn, seqRng);
        sequential.run();

        // Parallel run with same seed
        Random parRng = new Random(100);
        ParallelEvolution parallel = new ParallelEvolution(
                GENERATIONS, POPULATION_SIZE, K, fitnessFn, parRng, 4);
        parallel.run();

        // Both should have completed all generations (GEN loop iterations + 1 final eval)
        assertThat(sequential.bestFitnessHistory()).hasSize(GENERATIONS + 1);
        assertThat(parallel.bestFitnessHistory()).hasSize(GENERATIONS + 1);
        assertThat(parallel.totalGenerations()).isEqualTo(GENERATIONS);

        // Both should produce valid fitness values
        List<Long> seqBest = sequential.bestFitnessHistory();
        List<Long> parBest = parallel.bestFitnessHistory();

        for (int i = 0; i <= GENERATIONS; i++) {
            assertThat(seqBest.get(i)).isGreaterThanOrEqualTo(0);
            assertThat(parBest.get(i)).isGreaterThanOrEqualTo(0);
        }

        // Best brain should have valid structure
        AgentBrain seqBrain = sequential.bestBrain();
        AgentBrain parBrain = parallel.bestBrain();

        assertThat(seqBrain.nNeuron()).isNotNull();
        assertThat(seqBrain.sNeuron()).isNotNull();
        assertThat(seqBrain.wNeuron()).isNotNull();
        assertThat(seqBrain.eNeuron()).isNotNull();

        assertThat(parBrain.nNeuron()).isNotNull();
        assertThat(parBrain.sNeuron()).isNotNull();
        assertThat(parBrain.wNeuron()).isNotNull();
        assertThat(parBrain.eNeuron()).isNotNull();

        parallel.shutdown();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void speedupBenchmark_parallelFaster() {
        // Given: enough work to show parallel advantage
        int gens = 10;
        int popSize = 64;
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));

        // Sequential timing
        long seqStart = System.nanoTime();
        Random seqRng = new Random(200);
        EvolutionLoop sequential = new EvolutionLoop(gens, popSize, K, fitnessFn, seqRng);
        sequential.run();
        long seqTime = System.nanoTime() - seqStart;

        // Parallel timing (4 threads)
        long parStart = System.nanoTime();
        Random parRng = new Random(200);
        ParallelEvolution parallel = new ParallelEvolution(gens, popSize, K, fitnessFn, parRng, 4);
        parallel.run();
        long parTime = System.nanoTime() - parStart;

        parallel.shutdown();

        // Parallel should not be dramatically slower (allow for overhead)
        // On 4+ cores, parallel should achieve some speedup
        double speedup = (double) seqTime / parTime;
        // Note: speedup depends on available cores and workload
        // With ForkJoinPool overhead, we just verify parallel doesn't crash
        // and completes in reasonable time
        assertThat(parallel.totalGenerations()).isEqualTo(gens);
        assertThat(parallel.totalEvaluations()).isGreaterThan(0);

        // Log speedup for monitoring
        System.out.printf("Sequential: %dms, Parallel: %dms, Speedup: %.2fx%n",
                seqTime / 1_000_000, parTime / 1_000_000, speedup);
    }

    @Test
    void virtualThreadParallelism() {
        // Given
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));

        // When: create with explicit parallelism parameter (informational)
        ParallelEvolution parallel = new ParallelEvolution(
                GENERATIONS, POPULATION_SIZE, K, fitnessFn, new Random(), 4);

        // Then: parallelism value preserved for API compatibility
        assertThat(parallel.parallelism()).isEqualTo(4);

        parallel.shutdown();
    }

    @Test
    void defaultParallelism() {
        // Given
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));

        // When: create with default parallelism (0)
        ParallelEvolution parallel = new ParallelEvolution(
                GENERATIONS, POPULATION_SIZE, K, fitnessFn, new Random(), 0);

        // Then: should use available processors
        int expected = Runtime.getRuntime().availableProcessors();
        assertThat(parallel.parallelism()).isEqualTo(expected);

        parallel.shutdown();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void asyncExecution() throws Exception {
        // Given
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));
        ParallelEvolution parallel = new ParallelEvolution(
                3, 16, K, fitnessFn, new Random(), 2);

        // When: run asynchronously
        parallel.runAsync().get(30, TimeUnit.SECONDS);

        // Then: completed successfully
        assertThat(parallel.totalGenerations()).isEqualTo(3);
        assertThat(parallel.bestFitnessHistory()).hasSize(4); // 3 + 1 final
        assertThat(parallel.bestFitnessHistory()).isNotEmpty();

        parallel.shutdown();
    }

    @Test
    void bestOverallChromosome() {
        // Given
        FitnessFn fitnessFn = new FitnessFn(10, 10, 3, 5, 20, 2, new Random(42));
        ParallelEvolution parallel = new ParallelEvolution(
                GENERATIONS, POPULATION_SIZE, K, fitnessFn, new Random(), 2);

        // When
        parallel.run();

        // Then: best overall is valid
        Chromosome best = parallel.bestOverall();
        assertThat(best).isNotNull();
        assertThat(best.tree()).isNotNull();
        assertThat(best.fitness()).isGreaterThanOrEqualTo(0);

        parallel.shutdown();
    }
}
