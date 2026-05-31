package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;
import io.matrix.observability.MatrixMetrics;
import io.matrix.simulation.AgentBrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Main evolution loop: runs generations of mutation and fitness evaluation
 * for 4 populations (one per direction neuron).
 */
public class EvolutionLoop {

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

    private void evaluateGeneration() {
        evaluatePopulation(nPop);
        evaluatePopulation(sPop);
        evaluatePopulation(wPop);
        evaluatePopulation(ePop);
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
