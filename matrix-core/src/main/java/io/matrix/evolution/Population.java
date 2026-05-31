package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Population of chromosomes for one direction-neuron during evolution.
 *
 * <p>Manages selection, elitism, and reproduction via mutation and crossover.
 */
public class Population {

    private static final int TOURNAMENT_SIZE = 3;

    private final int size;
    private final int k;
    private final Random rng;
    private final List<Chromosome> chromosomes;

    public Population(int size, int k, Random rng) {
        this.size = size;
        this.k = k;
        this.rng = rng;
        this.chromosomes = new ArrayList<>(size);
    }

    public List<Chromosome> chromosomes() {
        return List.copyOf(chromosomes);
    }

    public void initialize() {
        chromosomes.clear();
        for (int i = 0; i < size; i++) {
            DecisionTree tree = DecisionTree.random(k, k / 2, rng);
            chromosomes.add(Chromosome.of(tree));
        }
    }

    public void updateFitness(List<Long> fitnesses) {
        for (int i = 0; i < chromosomes.size(); i++) {
            chromosomes.set(i, chromosomes.get(i).withFitness(fitnesses.get(i)));
        }
    }

    public Chromosome best() {
        return chromosomes.stream().max(Comparator.comparingLong(Chromosome::fitness)).orElseThrow();
    }

    public void evolve() {
        List<Chromosome> sorted = new ArrayList<>(chromosomes);
        sorted.sort((a, b) -> Long.compare(b.fitness(), a.fitness()));

        List<Chromosome> nextGen = new ArrayList<>();

        for (int i = 0; i < size / 2; i++) {
            nextGen.add(sorted.get(i));
        }

        while (nextGen.size() < size) {
            Chromosome parent1 = tournamentSelect(sorted);
            Chromosome parent2 = tournamentSelect(sorted);
            DecisionTree[] children = GeneticOperators.crossover(rng, parent1.tree(), parent2.tree());

            Chromosome child1 = parent1.withTree(mutate(children[0]));
            Chromosome child2 = parent2.withTree(mutate(children[1]));
            nextGen.add(child1);
            if (nextGen.size() < size) {
                nextGen.add(child2);
            }
        }

        chromosomes.clear();
        chromosomes.addAll(nextGen);
    }

    private Chromosome tournamentSelect(List<Chromosome> pool) {
        Chromosome best = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Chromosome candidate = pool.get(rng.nextInt(pool.size()));
            if (best == null || candidate.fitness() > best.fitness()) {
                best = candidate;
            }
        }
        return best;
    }

    private DecisionTree mutate(DecisionTree tree) {
        double roll = rng.nextDouble();
        if (roll < 0.15) return GeneticOperators.flipLeaf(rng, tree);
        if (roll < 0.40) return GeneticOperators.splitLeaf(rng, tree, k);
        if (roll < 0.55) return GeneticOperators.pruneTree(rng, tree);
        if (roll < 0.70) return GeneticOperators.changeInput(rng, tree, k);
        if (roll < 0.75) return GeneticOperators.swapChildren(rng, tree);
        if (roll < 0.85) return GeneticOperators.growSubtree(rng, tree, k);
        if (roll < 0.90) return GeneticOperators.compressBranch(rng, tree);
        return tree;
    }
}
