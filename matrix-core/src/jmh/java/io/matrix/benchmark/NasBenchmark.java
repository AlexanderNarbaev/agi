package io.matrix.benchmark;

import io.matrix.nas.ArchitectureEvaluator;
import io.matrix.nas.ArchitectureSpec;
import io.matrix.nas.ArchitectureSpec.Activation;
import io.matrix.nas.ArchitectureSpec.LayerSpec;
import io.matrix.nas.ArchitectureSpec.LayerType;
import io.matrix.nas.ArchitectureSpec.MutationResult;
import io.matrix.nas.LlmArchitectureOptimizer;
import io.matrix.nas.MutationOperator;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for NAS operations.
 *
 * <p>Compares random vs LLM-guided mutation, measures convergence speed,
 * and tracks architecture complexity metrics.
 *
 * <p>Run: {@code ./gradlew :matrix-core:jmh -PjmhBenchmark=NasBenchmark}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class NasBenchmark {

    private ArchitectureSpec smallArch;
    private ArchitectureSpec mediumArch;
    private ArchitectureSpec largeArch;
    private MutationOperator mutationOperator;
    private ArchitectureEvaluator evaluator;
    private LlmArchitectureOptimizer optimizer;
    private Random rng;

    @Setup
    public void setup() {
        rng = new Random(42);
        mutationOperator = new MutationOperator(rng);

        smallArch = ArchitectureSpec.random(2, 16, rng);
        mediumArch = ArchitectureSpec.random(4, 32, rng);
        largeArch = ArchitectureSpec.random(6, 64, rng);

        evaluator = new ArchitectureEvaluator(50, 20, rng);

        // LLM optimizer with mock backend (always returns NoOp for benchmarking)
        optimizer = new LlmArchitectureOptimizer(
                prompt -> java.util.concurrent.CompletableFuture.completedFuture(
                        "{\"mutation\": \"CHANGE_SIZE\", \"layer_index\": 0, \"parameter\": \"16\"}"),
                mutationOperator);
    }

    @TearDown
    public void tearDown() {
        evaluator.shutdown();
    }

    // ─── Mutation Benchmarks ───

    @Benchmark
    public MutationResult randomMutateSmall() {
        return mutationOperator.randomMutate(smallArch);
    }

    @Benchmark
    public MutationResult randomMutateMedium() {
        return mutationOperator.randomMutate(mediumArch);
    }

    @Benchmark
    public MutationResult randomMutateLarge() {
        return mutationOperator.randomMutate(largeArch);
    }

    @Benchmark
    public ArchitectureSpec applyMutationSmall() {
        MutationResult mutation = mutationOperator.randomMutate(smallArch);
        return smallArch.withMutation(mutation);
    }

    @Benchmark
    public ArchitectureSpec applyMutationMedium() {
        MutationResult mutation = mutationOperator.randomMutate(mediumArch);
        return mediumArch.withMutation(mutation);
    }

    // ─── LLM-Guided Mutation Benchmarks ───

    @Benchmark
    public MutationResult llmGuidedMutateSmall() {
        return optimizer.suggestMutationSync(smallArch, List.of(100L, 150L, 200L), "accuracy");
    }

    @Benchmark
    public MutationResult llmGuidedMutateMedium() {
        return optimizer.suggestMutationSync(mediumArch, List.of(100L, 150L, 200L), "accuracy");
    }

    // ─── Evaluation Benchmarks ───

    @Benchmark
    public long evaluateSmall() {
        return evaluator.evaluate(smallArch);
    }

    @Benchmark
    public long evaluateMedium() {
        return evaluator.evaluate(mediumArch);
    }

    @Benchmark
    public long evaluateLarge() {
        return evaluator.evaluate(largeArch);
    }

    @Benchmark
    public ArchitectureEvaluator.FitnessBreakdown evaluateDetailedSmall() {
        return evaluator.evaluateDetailed(smallArch);
    }

    // ─── Serialization Benchmarks ───

    @Benchmark
    public String serializePromptSmall() {
        return smallArch.toPromptString();
    }

    @Benchmark
    public String serializeJsonMedium() {
        return mediumArch.toJson();
    }

    @Benchmark
    public String generateCrispePrompt() {
        return optimizer.generateCrispePrompt(mediumArch, List.of(100L, 150L, 200L), "accuracy");
    }

    // ─── NAS Convergence Benchmark ───

    /**
     * Simulates a short NAS loop (5 generations) with random mutation.
     * Measures convergence speed.
     */
    @Benchmark
    public long nasConvergenceRandom() {
        ArchitectureSpec current = ArchitectureSpec.random(3, 16, rng);
        long bestFitness = evaluator.evaluate(current);

        for (int gen = 0; gen < 5; gen++) {
            MutationResult mutation = mutationOperator.randomMutate(current);
            ArchitectureSpec candidate = current.withMutation(mutation);
            long fitness = evaluator.evaluate(candidate);
            if (fitness >= bestFitness) {
                current = candidate;
                bestFitness = fitness;
            }
        }
        return bestFitness;
    }

    /**
     * Simulates a short NAS loop (5 generations) with LLM-guided mutation.
     */
    @Benchmark
    public long nasConvergenceLlmGuided() {
        ArchitectureSpec current = ArchitectureSpec.random(3, 16, rng);
        long bestFitness = evaluator.evaluate(current);
        List<Long> history = new ArrayList<>();
        history.add(bestFitness);

        for (int gen = 0; gen < 5; gen++) {
            MutationResult mutation = optimizer.suggestMutationSync(current, history, "accuracy");
            ArchitectureSpec candidate = current.withMutation(mutation);
            long fitness = evaluator.evaluate(candidate);
            if (fitness >= bestFitness) {
                current = candidate;
                bestFitness = fitness;
            }
            history.add(bestFitness);
        }
        return bestFitness;
    }

    // ─── Complexity Metrics ───

    @Benchmark
    public int architectureComplexity() {
        return mediumArch.complexity();
    }

    @Benchmark
    public int totalNeurons() {
        return largeArch.totalNeurons();
    }

    @Benchmark
    public int maxLayerSize() {
        return largeArch.maxLayerSize();
    }
}
