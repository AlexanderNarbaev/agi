package io.matrix.nas;

import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.HierarchicalBrain;
import io.matrix.neuron.NeuronLayer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Evaluates neural architecture fitness for NAS.
 *
 * <p>Measures three fitness dimensions:
 * <ul>
 *   <li><b>Accuracy</b> — inference correctness on synthetic test patterns</li>
 *   <li><b>Latency</b> — inference speed (inversely scored)</li>
 *   <li><b>Memory</b> — parameter count (inversely scored)</li>
 * </ul>
 *
 * <p>Supports parallel evaluation via virtual threads or
 * {@link CompletableFuture} for batch evaluation.
 *
 * <p>Thread-safe: uses per-evaluation local state; the shared
 * {@link Random} is used only for test pattern generation and is safe
 * for single-threaded use.
 */
public final class ArchitectureEvaluator {

    private final int testPatternCount;
    private final int inputBits;
    private final double accuracyWeight;
    private final double latencyWeight;
    private final double memoryWeight;
    private final Random rng;
    private final ExecutorService executor;

    /**
     * Creates an evaluator with default weights (0.6 accuracy, 0.2 latency, 0.2 memory).
     *
     * @param testPatternCount number of test patterns for accuracy evaluation
     * @param inputBits        input bit width
     * @param rng              random generator for test pattern generation
     */
    public ArchitectureEvaluator(int testPatternCount, int inputBits, Random rng) {
        this(testPatternCount, inputBits, 0.6, 0.2, 0.2, rng);
    }

    /**
     * Creates an evaluator with explicit fitness weights.
     *
     * @param testPatternCount number of test patterns
     * @param inputBits        input bit width
     * @param accuracyWeight   weight for accuracy component [0,1]
     * @param latencyWeight    weight for latency component [0,1]
     * @param memoryWeight     weight for memory component [0,1]
     * @param rng              random generator
     */
    public ArchitectureEvaluator(int testPatternCount, int inputBits,
                                  double accuracyWeight, double latencyWeight,
                                  double memoryWeight, Random rng) {
        this(testPatternCount, inputBits, accuracyWeight, latencyWeight, memoryWeight, rng,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates an evaluator with explicit executor for parallel evaluation.
     */
    public ArchitectureEvaluator(int testPatternCount, int inputBits,
                                  double accuracyWeight, double latencyWeight,
                                  double memoryWeight, Random rng,
                                  ExecutorService executor) {
        if (testPatternCount < 1) {
            throw new IllegalArgumentException("testPatternCount must be >= 1");
        }
        if (inputBits < 1) {
            throw new IllegalArgumentException("inputBits must be >= 1");
        }
        double total = accuracyWeight + latencyWeight + memoryWeight;
        if (Math.abs(total - 1.0) > 0.01) {
            throw new IllegalArgumentException("Weights must sum to 1.0, got: " + total);
        }
        this.testPatternCount = testPatternCount;
        this.inputBits = inputBits;
        this.accuracyWeight = accuracyWeight;
        this.latencyWeight = latencyWeight;
        this.memoryWeight = memoryWeight;
        this.rng = Objects.requireNonNull(rng);
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * Evaluates the fitness of an architecture specification.
     *
     * <p>Builds a {@link HierarchicalBrain} from the spec, runs inference
     * on synthetic test patterns, and computes a weighted fitness score.
     *
     * @param spec the architecture to evaluate
     * @return fitness score (higher is better, range [0, 1000])
     */
    public long evaluate(ArchitectureSpec spec) {
        Objects.requireNonNull(spec);
        if (spec.layers().isEmpty()) {
            return 0L;
        }

        try {
            HierarchicalBrain brain = buildBrain(spec);
            double accuracy = measureAccuracy(brain);
            double latency = measureLatency(brain);
            double memory = measureMemory(spec);

            return Math.round(
                    accuracyWeight * accuracy * 1000
                    + latencyWeight * latency * 1000
                    + memoryWeight * memory * 1000);
        } catch (Exception e) {
            // Invalid architecture → zero fitness
            return 0L;
        }
    }

    /**
     * Evaluates a batch of architectures in parallel.
     *
     * @param specs list of architectures to evaluate
     * @return list of fitness scores (same order as input)
     */
    public List<Long> evaluateBatch(List<ArchitectureSpec> specs) {
        Objects.requireNonNull(specs);
        List<CompletableFuture<Long>> futures = new ArrayList<>(specs.size());
        for (ArchitectureSpec spec : specs) {
            futures.add(CompletableFuture.supplyAsync(() -> evaluate(spec), executor));
        }
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * Returns detailed fitness breakdown for an architecture.
     *
     * @param spec the architecture to evaluate
     * @return fitness breakdown
     */
    public FitnessBreakdown evaluateDetailed(ArchitectureSpec spec) {
        Objects.requireNonNull(spec);
        if (spec.layers().isEmpty()) {
            return new FitnessBreakdown(0, 0, 0, 0, spec.complexity(), spec.totalNeurons());
        }

        try {
            HierarchicalBrain brain = buildBrain(spec);
            double accuracy = measureAccuracy(brain);
            double latency = measureLatency(brain);
            double memory = measureMemory(spec);
            long total = Math.round(
                    accuracyWeight * accuracy * 1000
                    + latencyWeight * latency * 1000
                    + memoryWeight * memory * 1000);

            return new FitnessBreakdown(total, accuracy, latency, memory,
                    spec.complexity(), spec.totalNeurons());
        } catch (Exception e) {
            return new FitnessBreakdown(0, 0, 0, 0, spec.complexity(), spec.totalNeurons());
        }
    }

    // ─── Brain Construction ───

    /**
     * Builds a HierarchicalBrain from an architecture spec.
     *
     * <p>Maps spec layers to NeuronLayers. If the spec has fewer than 3 layers,
     * duplicates the last layer. If more than 3, takes the first 3.
     */
    HierarchicalBrain buildBrain(ArchitectureSpec spec) {
        List<ArchitectureSpec.LayerSpec> layerSpecs = spec.layers();
        List<NeuronLayer> neuronLayers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            ArchitectureSpec.LayerSpec ls = layerSpecs.get(Math.min(i, layerSpecs.size() - 1));
            int k = Math.max(1, Math.min(ls.size(), 12));
            int neuronCount = Math.max(1, ls.size());
            List<DecisionTree> neurons = new ArrayList<>();
            for (int n = 0; n < neuronCount; n++) {
                neurons.add(DecisionTree.random(k, Math.min(k, 4), rng));
            }
            neuronLayers.add(new NeuronLayer(neurons, k));
        }

        return new HierarchicalBrain(neuronLayers.get(0), neuronLayers.get(1), neuronLayers.get(2));
    }

    // ─── Fitness Metrics ───

    /**
     * Measures inference accuracy on synthetic test patterns.
     *
     * <p>Generates random sensor inputs and checks if the brain produces
     * consistent (non-zero) outputs. Score = fraction of non-degenerate outputs.
     */
    double measureAccuracy(HierarchicalBrain brain) {
        int correct = 0;
        for (int i = 0; i < testPatternCount; i++) {
            long sensors = rng.nextLong() & 0xFFFFFL; // 20 bits
            int action = brain.decide(sensors);
            // Non-degenerate output (not always 0 or 31) counts as correct
            if (action > 0 && action < 31) {
                correct++;
            }
        }
        return (double) correct / testPatternCount;
    }

    /**
     * Measures inference latency (inversely scored).
     *
     * <p>Runs multiple inferences and scores based on speed.
     * Faster = higher score. Normalized to [0, 1].
     */
    double measureLatency(HierarchicalBrain brain) {
        long sensors = rng.nextLong() & 0xFFFFFL;
        int iterations = 1000;

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            brain.decide(sensors);
        }
        long elapsed = System.nanoTime() - start;

        // Normalize: 1ms for 1000 ops = perfect score
        double opsPerMs = iterations / Math.max(1.0, elapsed / 1_000_000.0);
        return Math.min(1.0, opsPerMs / 1000.0); // 1000 ops/ms = score 1.0
    }

    /**
     * Measures memory efficiency (inversely scored).
     *
     * <p>Fewer parameters = higher score. Normalized to [0, 1].
     */
    double measureMemory(ArchitectureSpec spec) {
        int neurons = spec.totalNeurons();
        // Assume max 100 neurons for normalization
        return Math.max(0, 1.0 - (neurons / 100.0));
    }

    // ─── Shutdown ───

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    // ─── Records ───

    /**
     * Detailed fitness breakdown for an architecture.
     */
    public record FitnessBreakdown(
            long totalFitness,
            double accuracy,
            double latency,
            double memory,
            int complexity,
            int totalNeurons
    ) {
        @Override
        public String toString() {
            return String.format(
                    "FitnessBreakdown{total=%d, acc=%.3f, lat=%.3f, mem=%.3f, complexity=%d, neurons=%d}",
                    totalFitness, accuracy, latency, memory, complexity, totalNeurons);
        }
    }
}
