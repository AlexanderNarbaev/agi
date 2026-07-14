package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.observability.MatrixMetrics;
import io.matrix.simulation.AgentBrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Main evolution loop: runs generations of mutation and fitness evaluation
 * for 4 populations (one per direction neuron).
 *
 * <p>Supports both sequential and parallel (virtual-thread-based) evaluation
 * of the four directional populations per generation.
 */
public class EvolutionLoop {

    /** Virtual-thread-per-task executor for parallel population evaluation. */
    private static final Executor VT_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    private final int generations;
    private final int populationSize;
    private final int k;
    private final FitnessFn fitnessFn;
    private final Random rng;
    private final MatrixMetrics metrics;

    private final Population nPop;
    private final Population sPop;
    private final Population wPop;
    private final Population ePop;

    private final List<Long> bestFitnessHistory = new ArrayList<>();
    private final List<Long> avgFitnessHistory = new ArrayList<>();

    public EvolutionLoop(int generations, int populationSize, int k,
                          FitnessFn fitnessFn, MatrixMetrics metrics, Random rng) {
        this.generations = generations;
        this.populationSize = populationSize;
        this.k = k;
        this.fitnessFn = fitnessFn;
        this.metrics = metrics;
        this.rng = rng;
        this.nPop = new Population(populationSize, k, rng);
        this.sPop = new Population(populationSize, k, rng);
        this.wPop = new Population(populationSize, k, rng);
        this.ePop = new Population(populationSize, k, rng);
    }

    public EvolutionLoop(int generations, int populationSize, int k,
                          FitnessFn fitnessFn, Random rng) {
        this(generations, populationSize, k, fitnessFn, null, rng);
    }

    public List<Long> bestFitnessHistory() { return List.copyOf(bestFitnessHistory); }
    public List<Long> avgFitnessHistory() { return List.copyOf(avgFitnessHistory); }

    public AgentBrain bestBrain() {
        return new AgentBrain(
                nPop.best().tree(), sPop.best().tree(),
                wPop.best().tree(), ePop.best().tree());
    }

    public Chromosome bestOverall() {
        Chromosome[] all = {nPop.best(), sPop.best(), wPop.best(), ePop.best()};
        Chromosome best = all[0];
        for (int i = 1; i < all.length; i++) {
            if (all[i].fitness() > best.fitness()) best = all[i];
        }
        return best;
    }

    public void run() {
        nPop.initialize();
        sPop.initialize();
        wPop.initialize();
        ePop.initialize();

        for (int gen = 0; gen < generations; gen++) {
            if (metrics != null) metrics.evolutionGeneration();
            evaluateGeneration();
            recordHistory();
            if (metrics != null) {
                metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
                metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
            }
            nPop.evolve();
            sPop.evolve();
            wPop.evolve();
            ePop.evolve();
        }
        evaluateGeneration();
        recordHistory();
        if (metrics != null) {
            metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
            metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
        }
    }

    /**
     * Runs the evolution loop with parallel population evaluation per generation
     * using {@link CompletableFuture} on virtual threads. The 4 directional
     * populations (N/S/W/E) are evaluated concurrently.
     */
    public void runParallel() {
        nPop.initialize();
        sPop.initialize();
        wPop.initialize();
        ePop.initialize();

        for (int gen = 0; gen < generations; gen++) {
            if (metrics != null) metrics.evolutionGeneration();
            evaluateGenerationParallel();
            recordHistory();
            if (metrics != null) {
                metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
                metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
            }
            nPop.evolve();
            sPop.evolve();
            wPop.evolve();
            ePop.evolve();
        }
        evaluateGenerationParallel();
        recordHistory();
        if (metrics != null) {
            metrics.fitnessBest(bestFitnessHistory.get(bestFitnessHistory.size() - 1));
            metrics.fitnessAvg(avgFitnessHistory.get(avgFitnessHistory.size() - 1));
        }
    }

    private void evaluateGeneration() {
        evaluatePopulation(nPop);
        evaluatePopulation(sPop);
        evaluatePopulation(wPop);
        evaluatePopulation(ePop);
    }

    /**
     * Evaluates all 4 directional populations concurrently using
     * {@link CompletableFuture} backed by virtual threads.
     * Each population's fitness evaluation is an independent subtask.
     */
    private void evaluateGenerationParallel() {
        var nFuture = CompletableFuture.runAsync(() -> evaluatePopulation(nPop), VT_EXECUTOR);
        var sFuture = CompletableFuture.runAsync(() -> evaluatePopulation(sPop), VT_EXECUTOR);
        var wFuture = CompletableFuture.runAsync(() -> evaluatePopulation(wPop), VT_EXECUTOR);
        var eFuture = CompletableFuture.runAsync(() -> evaluatePopulation(ePop), VT_EXECUTOR);

        // Wait for all four population evaluations to complete
        CompletableFuture.allOf(nFuture, sFuture, wFuture, eFuture).join();
    }

    private void evaluatePopulation(Population pop) {
        List<Chromosome> chs = pop.chromosomes();
        List<Long> fitnesses = new ArrayList<>();

        for (int i = 0; i < chs.size(); i++) {
            Chromosome n = (pop == nPop) ? chs.get(i) : nPop.chromosomes().get(i);
            Chromosome s = (pop == sPop) ? chs.get(i) : sPop.chromosomes().get(i);
            Chromosome w = (pop == wPop) ? chs.get(i) : wPop.chromosomes().get(i);
            Chromosome e = (pop == ePop) ? chs.get(i) : ePop.chromosomes().get(i);

            long fitness = fitnessFn.evaluate(n, s, w, e);
            fitnesses.add(fitness);
        }
        pop.updateFitness(fitnesses);
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
}
