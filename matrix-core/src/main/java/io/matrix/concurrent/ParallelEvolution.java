package io.matrix.concurrent;

import io.matrix.evolution.Chromosome;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.FitnessFn;
import io.matrix.evolution.Population;
import io.matrix.neuron.DecisionTree;
import io.matrix.simulation.AgentBrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel evolution engine using virtual threads (JEP 444) for concurrent
 * population evaluation. Each of the 4 directional populations (N/S/W/E)
 * is evaluated in its own lightweight virtual thread.
 *
 * <p>Key features:
 * <ul>
 *   <li>Virtual-thread-per-task executor for population evaluation</li>
 *   <li>{@link CompletableFuture} for async orchestration</li>
 *   <li>Thread-safe metrics via {@link AtomicLong}</li>
 *   <li>No manual pool lifecycle — virtual threads are JVM-managed</li>
 * </ul>
 *
 * <p>Ref: Phase8 — Multithreading & Concurrency (virtual threads)
 */
public final class ParallelEvolution {

    /** Virtual-thread-per-task executor. No need to shut down manually. */
    private static final Executor VT_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    private final int generations;
    private final int populationSize;
    private final int k;
    private final FitnessFn fitnessFn;
    private final Random rng;
    private final int parallelism;

    // Metrics
    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalGenerations = new AtomicLong(0);

    private Population nPop;
    private Population sPop;
    private Population wPop;
    private Population ePop;

    private final List<Long> bestFitnessHistory =
            Collections.synchronizedList(new ArrayList<>());
    private final List<Long> avgFitnessHistory =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a parallel evolution engine.
     *
     * @param generations    number of generations to run
     * @param populationSize size of each population
     * @param k              input width per neuron
     * @param fitnessFn      fitness function
     * @param rng            random generator
     * @param parallelism    ignored (retained for API compatibility); virtual threads
     *                       scale automatically
     */
    public ParallelEvolution(int generations, int populationSize, int k,
                             FitnessFn fitnessFn, Random rng, int parallelism) {
        this.generations = generations;
        this.populationSize = populationSize;
        this.k = k;
        this.fitnessFn = fitnessFn;
        this.rng = rng;
        this.parallelism = parallelism > 0 ? parallelism : Runtime.getRuntime().availableProcessors();
    }

    /**
     * Creates with default parallelism (available processors — informational only).
     */
    public ParallelEvolution(int generations, int populationSize, int k,
                             FitnessFn fitnessFn, Random rng) {
        this(generations, populationSize, k, fitnessFn, rng, 0);
    }

    /**
     * Returns the best fitness history.
     */
    public List<Long> bestFitnessHistory() {
        return List.copyOf(bestFitnessHistory);
    }

    /**
     * Returns the average fitness history.
     */
    public List<Long> avgFitnessHistory() {
        return List.copyOf(avgFitnessHistory);
    }

    /**
     * Returns the best brain from the final generation.
     */
    public AgentBrain bestBrain() {
        return new AgentBrain(
                nPop.best().tree(), sPop.best().tree(),
                wPop.best().tree(), ePop.best().tree());
    }

    /**
     * Returns the best overall chromosome.
     */
    public Chromosome bestOverall() {
        Chromosome[] all = {nPop.best(), sPop.best(), wPop.best(), ePop.best()};
        Chromosome best = all[0];
        for (int i = 1; i < all.length; i++) {
            if (all[i].fitness() > best.fitness()) best = all[i];
        }
        return best;
    }

    /**
     * Runs the evolution synchronously with parallel population evaluation
     * on virtual threads.
     */
    public void run() {
        nPop = new Population(populationSize, k, rng);
        sPop = new Population(populationSize, k, rng);
        wPop = new Population(populationSize, k, rng);
        ePop = new Population(populationSize, k, rng);

        nPop.initialize();
        sPop.initialize();
        wPop.initialize();
        ePop.initialize();

        for (int gen = 0; gen < generations; gen++) {
            evaluateGenerationParallel();
            recordHistory();
            totalGenerations.incrementAndGet();

            nPop.evolve();
            sPop.evolve();
            wPop.evolve();
            ePop.evolve();
        }
        evaluateGenerationParallel();
        recordHistory();
    }

    /**
     * Runs the evolution asynchronously using a virtual thread.
     *
     * @return CompletableFuture that completes when evolution finishes
     */
    public CompletableFuture<Void> runAsync() {
        return CompletableFuture.runAsync(this::run, VT_EXECUTOR);
    }

    /**
     * Evaluates all 4 populations in parallel using virtual threads.
     */
    private void evaluateGenerationParallel() {
        Population[] pops = {nPop, sPop, wPop, ePop};

        var nFuture = CompletableFuture.supplyAsync(() -> evaluatePopulation(pops[0]), VT_EXECUTOR);
        var sFuture = CompletableFuture.supplyAsync(() -> evaluatePopulation(pops[1]), VT_EXECUTOR);
        var wFuture = CompletableFuture.supplyAsync(() -> evaluatePopulation(pops[2]), VT_EXECUTOR);
        var eFuture = CompletableFuture.supplyAsync(() -> evaluatePopulation(pops[3]), VT_EXECUTOR);

        // Wait for all to complete
        CompletableFuture.allOf(nFuture, sFuture, wFuture, eFuture).join();

        // Update fitness values
        pops[0].updateFitness(nFuture.join());
        pops[1].updateFitness(sFuture.join());
        pops[2].updateFitness(wFuture.join());
        pops[3].updateFitness(eFuture.join());
    }

    /**
     * Evaluates a single population's chromosomes and returns fitness values.
     */
    private List<Long> evaluatePopulation(Population pop) {
        List<Chromosome> chs = pop.chromosomes();
        List<Long> fitnesses = new ArrayList<>(chs.size());

        for (int i = 0; i < chs.size(); i++) {
            Chromosome n = chs.get(i);
            Chromosome s = sPop.chromosomes().get(
                    Math.min(i, sPop.chromosomes().size() - 1));
            Chromosome w = wPop.chromosomes().get(
                    Math.min(i, wPop.chromosomes().size() - 1));
            Chromosome e = ePop.chromosomes().get(
                    Math.min(i, ePop.chromosomes().size() - 1));

            long fitness = fitnessFn.evaluate(n, s, w, e);
            fitnesses.add(fitness);
            totalEvaluations.incrementAndGet();
        }
        return fitnesses;
    }

    private void recordHistory() {
        long total = 0;
        long best = Long.MIN_VALUE;

        for (Population pop : List.of(nPop, sPop, wPop, ePop)) {
            for (Chromosome ch : pop.chromosomes()) {
                long f = ch.fitness();
                total += f;
                if (f > best) best = f;
            }
        }
        bestFitnessHistory.add(best);
        avgFitnessHistory.add(total / (4L * populationSize));
    }

    /**
     * Returns the total number of fitness evaluations performed.
     */
    public long totalEvaluations() {
        return totalEvaluations.get();
    }

    /**
     * Returns the total number of generations completed.
     */
    public long totalGenerations() {
        return totalGenerations.get();
    }

    /**
     * Returns the configured parallelism level (informational only;
     * virtual threads auto-scale).
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * No-op: virtual threads are JVM-managed and do not require explicit shutdown.
     * Kept for backward compatibility.
     */
    public void shutdown() {
        // Virtual threads auto-cleanup — no explicit shutdown needed
    }
}
