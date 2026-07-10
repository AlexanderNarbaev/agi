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
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Parallel evolution engine using {@link ForkJoinPool} for concurrent
 * population evaluation with work-stealing load balancing.
 *
 * <p>Key features:
 * <ul>
 *   <li>ForkJoinPool with configurable parallelism for population evaluation</li>
 *   <li>Work-stealing algorithm: each population evaluation is a RecursiveTask</li>
 *   <li>{@link CompletableFuture} for async results</li>
 *   <li>Thread-safe metrics via {@link AtomicLong}</li>
 * </ul>
 *
 * <p>Ref: Phase8 — Multithreading & Concurrency
 */
public final class ParallelEvolution {

    private final int generations;
    private final int populationSize;
    private final int k;
    private final FitnessFn fitnessFn;
    private final Random rng;
    private final int parallelism;
    private final ForkJoinPool pool;

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
     * @param parallelism    ForkJoinPool parallelism level (0 = default)
     */
    public ParallelEvolution(int generations, int populationSize, int k,
                             FitnessFn fitnessFn, Random rng, int parallelism) {
        this.generations = generations;
        this.populationSize = populationSize;
        this.k = k;
        this.fitnessFn = fitnessFn;
        this.rng = rng;
        this.parallelism = parallelism > 0 ? parallelism : Runtime.getRuntime().availableProcessors();
        this.pool = new ForkJoinPool(this.parallelism);
    }

    /**
     * Creates with default parallelism (available processors).
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
     * Runs the evolution synchronously with parallel population evaluation.
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
     * Runs the evolution asynchronously using CompletableFuture.
     *
     * @return CompletableFuture that completes when evolution finishes
     */
    public CompletableFuture<Void> runAsync() {
        return CompletableFuture.runAsync(this::run, pool);
    }

    /**
     * Evaluates all 4 populations in parallel using ForkJoinPool.
     */
    private void evaluateGenerationParallel() {
        Population[] pops = {nPop, sPop, wPop, ePop};
        List<RecursiveTask<List<Long>>> tasks = new ArrayList<>(4);

        for (Population pop : pops) {
            tasks.add(new PopulationEvalTask(pop));
        }

        // Fork all tasks
        for (var task : tasks) {
            pool.invoke(task);
        }

        // Join and update fitness
        for (int i = 0; i < 4; i++) {
            pops[i].updateFitness(tasks.get(i).join());
        }
    }

    /**
     * RecursiveTask for evaluating a single population using work-stealing.
     */
    private class PopulationEvalTask extends RecursiveTask<List<Long>> {

        private static final int THRESHOLD = 16;
        private final Population pop;

        PopulationEvalTask(Population pop) {
            this.pop = pop;
        }

        @Override
        protected List<Long> compute() {
            List<Chromosome> chs = pop.chromosomes();
            if (chs.size() <= THRESHOLD) {
                return evaluateChunk(chs);
            }

            int mid = chs.size() / 2;
            PopulationEvalTask left = new PopulationEvalTask(subPopulation(chs, 0, mid));
            PopulationEvalTask right = new PopulationEvalTask(subPopulation(chs, mid, chs.size()));

            left.fork();
            List<Long> rightResult = right.compute();
            List<Long> leftResult = left.join();

            List<Long> combined = new ArrayList<>(leftResult.size() + rightResult.size());
            combined.addAll(leftResult);
            combined.addAll(rightResult);
            return combined;
        }

        private Population subPopulation(List<Chromosome> chs, int from, int to) {
            Population sub = new Population(to - from, k, new Random(rng.nextLong()));
            sub.initialize();
            return sub;
        }

        private List<Long> evaluateChunk(List<Chromosome> chs) {
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
     * Returns the ForkJoinPool parallelism level.
     */
    public int parallelism() {
        return parallelism;
    }

    /**
     * Returns the ForkJoinPool instance.
     */
    public ForkJoinPool pool() {
        return pool;
    }

    /**
     * Shuts down the ForkJoinPool. Must be called when done.
     */
    public void shutdown() {
        pool.shutdown();
    }
}
