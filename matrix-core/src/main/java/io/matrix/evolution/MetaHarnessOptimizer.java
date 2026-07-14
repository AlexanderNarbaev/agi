package io.matrix.evolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * Meta-harness genetic optimizer — evolves agent configuration
 * (tool availability, memory depth, workflow stages, safety thresholds)
 * using a Pareto multi-objective fitness function.
 *
 * <p>Based on research pattern: "genetic evolution of the harness, not the model".
 * Each {@link HarnessConfig} is a genome with 6 parameters controlling agent
 * behavior. The outer loop applies mutation, uniform crossover, elite selection,
 * and Pareto multi-objective evaluation.
 */
public final class MetaHarnessOptimizer {

    // ── Parameter ranges ──────────────────────────────────────────────

    private static final int MIN_TOOL_COUNT = 1;
    private static final int MAX_TOOL_COUNT = 50;

    private static final int MIN_MEMORY_DEPTH = 1;
    private static final int MAX_MEMORY_DEPTH = 10;

    private static final int MIN_WORKFLOW_STAGES = 1;
    private static final int MAX_WORKFLOW_STAGES = 20;

    private static final double MIN_SAFETY_THRESHOLD = 0.1;
    private static final double MAX_SAFETY_THRESHOLD = 1.0;

    private static final double MIN_EXPLORATION_RATE = 0.0;
    private static final double MAX_EXPLORATION_RATE = 1.0;

    private static final int MIN_BATCH_SIZE = 1;
    private static final int MAX_BATCH_SIZE = 1000;

    private static final double MUTATION_CHANCE = 0.3;

    // ── Types ─────────────────────────────────────────────────────────

    /**
     * Agent harness configuration — the evolvable genome.
     *
     * @param toolCount        number of available tools
     * @param memoryDepth      L0-L4 memory depth
     * @param maxWorkflowStages maximum reasoning stages
     * @param safetyThreshold  0.0-1.0 safety gate
     * @param explorationRate  0.0-1.0 exploration probability
     * @param batchSize        retraining batch size
     */
    public record HarnessConfig(
            int toolCount,
            int memoryDepth,
            int maxWorkflowStages,
            double safetyThreshold,
            double explorationRate,
            int batchSize
    ) {
        public HarnessConfig {
            validateInt(toolCount, MIN_TOOL_COUNT, MAX_TOOL_COUNT, "toolCount");
            validateInt(memoryDepth, MIN_MEMORY_DEPTH, MAX_MEMORY_DEPTH, "memoryDepth");
            validateInt(maxWorkflowStages, MIN_WORKFLOW_STAGES, MAX_WORKFLOW_STAGES,
                    "maxWorkflowStages");
            validateDouble(safetyThreshold, MIN_SAFETY_THRESHOLD, MAX_SAFETY_THRESHOLD,
                    "safetyThreshold");
            validateDouble(explorationRate, MIN_EXPLORATION_RATE, MAX_EXPLORATION_RATE,
                    "explorationRate");
            validateInt(batchSize, MIN_BATCH_SIZE, MAX_BATCH_SIZE, "batchSize");
        }

        private static void validateInt(int value, int min, int max, String name) {
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        name + "=" + value + " out of [" + min + ", " + max + "]");
            }
        }

        private static void validateDouble(double value, double min, double max, String name) {
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        name + "=" + value + " out of [" + min + ", " + max + "]");
            }
        }

        /** Creates a random config with all parameters within valid ranges. */
        static HarnessConfig random(Random rng) {
            return new HarnessConfig(
                    randomInt(rng, MIN_TOOL_COUNT, MAX_TOOL_COUNT),
                    randomInt(rng, MIN_MEMORY_DEPTH, MAX_MEMORY_DEPTH),
                    randomInt(rng, MIN_WORKFLOW_STAGES, MAX_WORKFLOW_STAGES),
                    randomDouble(rng, MIN_SAFETY_THRESHOLD, MAX_SAFETY_THRESHOLD),
                    randomDouble(rng, MIN_EXPLORATION_RATE, MAX_EXPLORATION_RATE),
                    randomInt(rng, MIN_BATCH_SIZE, MAX_BATCH_SIZE)
            );
        }

        private static int randomInt(Random rng, int min, int max) {
            return min + rng.nextInt(max - min + 1);
        }

        private static double randomDouble(Random rng, double min, double max) {
            return min + rng.nextDouble() * (max - min);
        }
    }

    /**
     * A candidate harness configuration with its scalar fitness and
     * Pareto multi-objective fitness vector.
     */
    public record HarnessCandidate(
            HarnessConfig config,
            double fitness,
            ParetoFitness.FitnessVector vector
    ) {}

    // ── Fields ────────────────────────────────────────────────────────

    private final ToDoubleFunction<HarnessConfig> evaluator;
    private final int populationSize;
    private final int generations;
    private final Random rng;
    private final List<Double> fitnessHistory = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────

    /**
     * @param evaluator      scores a {@link HarnessConfig} (higher = better)
     * @param populationSize must be &ge; 2
     * @param generations    must be &ge; 1
     * @param rng            source of randomness (seedable for reproducibility)
     */
    public MetaHarnessOptimizer(
            ToDoubleFunction<HarnessConfig> evaluator,
            int populationSize,
            int generations,
            Random rng
    ) {
        if (populationSize < 2) {
            throw new IllegalArgumentException("populationSize must be >= 2, got " + populationSize);
        }
        if (generations < 1) {
            throw new IllegalArgumentException("generations must be >= 1, got " + generations);
        }
        this.evaluator = evaluator;
        this.populationSize = populationSize;
        this.generations = generations;
        this.rng = rng;
    }

    // ── Public API ────────────────────────────────────────────────────

    /** Creates a random initial population of {@link HarnessConfig}s. */
    public List<HarnessConfig> initialize() {
        List<HarnessConfig> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            population.add(HarnessConfig.random(rng));
        }
        return population;
    }

    /**
     * Mutates a config by perturbing each parameter by &plusmn;20% of its
     * valid range, then clamping to bounds.
     */
    public HarnessConfig mutate(HarnessConfig parent) {
        return new HarnessConfig(
                clampInt(mutateInt(parent.toolCount(),
                        MIN_TOOL_COUNT, MAX_TOOL_COUNT), MIN_TOOL_COUNT, MAX_TOOL_COUNT),
                clampInt(mutateInt(parent.memoryDepth(),
                        MIN_MEMORY_DEPTH, MAX_MEMORY_DEPTH), MIN_MEMORY_DEPTH, MAX_MEMORY_DEPTH),
                clampInt(mutateInt(parent.maxWorkflowStages(),
                        MIN_WORKFLOW_STAGES, MAX_WORKFLOW_STAGES),
                        MIN_WORKFLOW_STAGES, MAX_WORKFLOW_STAGES),
                clampDouble(mutateDouble(parent.safetyThreshold(),
                        MIN_SAFETY_THRESHOLD, MAX_SAFETY_THRESHOLD),
                        MIN_SAFETY_THRESHOLD, MAX_SAFETY_THRESHOLD),
                clampDouble(mutateDouble(parent.explorationRate(),
                        MIN_EXPLORATION_RATE, MAX_EXPLORATION_RATE),
                        MIN_EXPLORATION_RATE, MAX_EXPLORATION_RATE),
                clampInt(mutateInt(parent.batchSize(),
                        MIN_BATCH_SIZE, MAX_BATCH_SIZE), MIN_BATCH_SIZE, MAX_BATCH_SIZE)
        );
    }

    /** Uniform crossover: each parameter taken from a or b with equal probability. */
    public HarnessConfig crossover(HarnessConfig a, HarnessConfig b) {
        return new HarnessConfig(
                rng.nextBoolean() ? a.toolCount() : b.toolCount(),
                rng.nextBoolean() ? a.memoryDepth() : b.memoryDepth(),
                rng.nextBoolean() ? a.maxWorkflowStages() : b.maxWorkflowStages(),
                rng.nextBoolean() ? a.safetyThreshold() : b.safetyThreshold(),
                rng.nextBoolean() ? a.explorationRate() : b.explorationRate(),
                rng.nextBoolean() ? a.batchSize() : b.batchSize()
        );
    }

    /**
     * Evaluates all configs in the population, computing scalar fitness
     * (via the evaluator) and a Pareto multi-objective vector.
     */
    public List<HarnessCandidate> evaluateGeneration(List<HarnessConfig> population) {
        int n = population.size();
        double[] rawFitnesses = new double[n];
        double maxFitness = 0.0;

        for (int i = 0; i < n; i++) {
            rawFitnesses[i] = evaluator.applyAsDouble(population.get(i));
            maxFitness = Math.max(maxFitness, rawFitnesses[i]);
        }

        List<HarnessCandidate> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            HarnessConfig config = population.get(i);
            double fitness = rawFitnesses[i];

            double quality = maxFitness > 0.0 ? fitness / maxFitness : 0.0;
            double robustness = 0.4 * config.safetyThreshold()
                    + 0.3 * (config.memoryDepth() / (double) MAX_MEMORY_DEPTH)
                    + 0.3 * config.explorationRate();
            double latencyMs = config.maxWorkflowStages() * 100.0;
            int complexity = config.toolCount();

            ParetoFitness.FitnessVector vector =
                    ParetoFitness.FitnessVector.of(quality, robustness, latencyMs, complexity);

            candidates.add(new HarnessCandidate(config, fitness, vector));
        }

        return candidates;
    }

    /**
     * Runs the full genetic optimization loop and returns the best harness
     * configuration found.
     */
    public HarnessConfig optimize() {
        fitnessHistory.clear();

        List<HarnessConfig> population = initialize();

        for (int gen = 0; gen < generations; gen++) {
            List<HarnessCandidate> candidates = evaluateGeneration(population);

            double bestFitness = candidates.stream()
                    .mapToDouble(HarnessCandidate::fitness)
                    .max()
                    .orElse(0.0);
            fitnessHistory.add(bestFitness);

            population = selectNextGeneration(candidates);
        }

        List<HarnessCandidate> finalCandidates = evaluateGeneration(population);
        double finalBest = finalCandidates.stream()
                .mapToDouble(HarnessCandidate::fitness)
                .max()
                .orElse(0.0);
        fitnessHistory.add(finalBest);

        return finalCandidates.stream()
                .max(Comparator.comparingDouble(HarnessCandidate::fitness))
                .map(HarnessCandidate::config)
                .orElseThrow(() -> new IllegalStateException("empty population"));
    }

    /** Returns the best scalar fitness recorded at each generation. */
    public List<Double> fitnessHistory() {
        return List.copyOf(fitnessHistory);
    }

    // ── Internal ──────────────────────────────────────────────────────

    /**
     * Elite selection: top 50% survive; remainder filled by crossover
     * of elite parents with random mutation.
     */
    private List<HarnessConfig> selectNextGeneration(List<HarnessCandidate> candidates) {
        candidates.sort((a, b) -> Double.compare(b.fitness(), a.fitness()));

        int eliteCount = Math.max(1, populationSize / 2);
        List<HarnessConfig> nextGen = new ArrayList<>(populationSize);

        for (int i = 0; i < eliteCount; i++) {
            nextGen.add(candidates.get(i).config());
        }

        while (nextGen.size() < populationSize) {
            HarnessConfig parentA = candidates.get(rng.nextInt(eliteCount)).config();
            HarnessConfig parentB = candidates.get(rng.nextInt(eliteCount)).config();
            HarnessConfig child = crossover(parentA, parentB);

            if (rng.nextDouble() < MUTATION_CHANCE) {
                child = mutate(child);
            }

            nextGen.add(child);
        }

        return nextGen;
    }

    // ── Mutation helpers ─────────────────────────────────────────────

    /** Perturbs value by &plusmn;20% of the given [min, max] range. */
    private int mutateInt(int value, int min, int max) {
        double perturbation = (rng.nextDouble() - 0.5) * 0.4;
        int delta = (int) Math.round(perturbation * (max - min));
        return value + delta;
    }

    private double mutateDouble(double value, double min, double max) {
        double perturbation = (rng.nextDouble() - 0.5) * 0.4;
        return value + perturbation * (max - min);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
