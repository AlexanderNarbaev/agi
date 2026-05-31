package io.matrix.evolution;

import io.matrix.neuron.DecisionTree;

import java.util.Objects;
import java.util.UUID;

/**
 * A chromosome wraps a {@link DecisionTree} with fitness and generation metadata
 * for evolutionary optimization.
 *
 * <p>Ref: L5_DNA.md §2.1
 */
public final class Chromosome {

    private final UUID uuid;
    private final long generation;
    private final DecisionTree tree;
    private final long fitness;

    private Chromosome(UUID uuid, long generation, DecisionTree tree, long fitness) {
        this.uuid = Objects.requireNonNull(uuid);
        this.generation = generation;
        this.tree = Objects.requireNonNull(tree);
        this.fitness = fitness;
    }

    public static Chromosome of(DecisionTree tree) {
        return new Chromosome(UUID.randomUUID(), 0L, tree, 0L);
    }

    public static Chromosome of(UUID uuid, long generation, DecisionTree tree, long fitness) {
        return new Chromosome(uuid, generation, tree, fitness);
    }

    public Chromosome withTree(DecisionTree newTree) {
        return new Chromosome(uuid, generation + 1, newTree, 0L);
    }

    public Chromosome withFitness(long newFitness) {
        return new Chromosome(uuid, generation, tree, newFitness);
    }

    public UUID uuid() { return uuid; }

    public long generation() { return generation; }

    public DecisionTree tree() { return tree; }

    public long fitness() { return fitness; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Chromosome that)) return false;
        return generation == that.generation
                && fitness == that.fitness
                && uuid.equals(that.uuid)
                && tree.equals(that.tree);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, generation, tree, fitness);
    }

    @Override
    public String toString() {
        return "Chromosome{uuid=" + uuid
                + ", gen=" + generation
                + ", fitness=" + fitness
                + ", tree=" + tree + '}';
    }
}
