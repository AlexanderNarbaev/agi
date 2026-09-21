package io.matrix.cluster;

import io.matrix.neuron.TruthTable;
import io.matrix.neuron.WeightVector;

import java.util.Objects;

/**
 * Wraps a TruthTable with runtime state for the cluster actor.
 */
public final class NeuronInstance {

    public enum State { STABLE, LEARNING, MUTATING, FROZEN }

    private final NeuronId id;
    private final TruthTable truthTable;
    private final State state;
    private final WeightVector weights;
    private volatile boolean lastOutput;

    public NeuronInstance(NeuronId id, TruthTable truthTable, State state) {
        this(id, truthTable, state, truthTable.weights());
    }

    public NeuronInstance(NeuronId id, TruthTable truthTable, State state, WeightVector weights) {
        this.id = Objects.requireNonNull(id);
        this.truthTable = Objects.requireNonNull(truthTable);
        this.state = Objects.requireNonNull(state);
        this.weights = weights;
    }

    public static NeuronInstance stable(NeuronId id, TruthTable table) {
        return new NeuronInstance(id, table, State.STABLE);
    }

    public static NeuronInstance stable(NeuronId id, TruthTable table, WeightVector weights) {
        return new NeuronInstance(id, table, State.STABLE, weights);
    }

    public static NeuronInstance frozen(NeuronId id, TruthTable table) {
        return new NeuronInstance(id, table, State.FROZEN);
    }

    public static NeuronInstance frozen(NeuronId id, TruthTable table, WeightVector weights) {
        return new NeuronInstance(id, table, State.FROZEN, weights);
    }

    public NeuronId id() { return id; }

    public TruthTable truthTable() { return truthTable; }

    public State state() { return state; }

    /**
     * Returns the optional priority weights for this neuron.
     *
     * <p>May be {@code null} if no weights are assigned. When non-null,
     * evaluation permutes inputs by priority order.
     *
     * @return weight vector or null
     */
    public WeightVector weights() { return weights; }

    public int k() { return truthTable.k(); }

    public boolean lastOutput() { return lastOutput; }

    public void setLastOutput(boolean value) { this.lastOutput = value; }

    public boolean isFrozen() { return state == State.FROZEN; }

    public boolean isMutable() { return state == State.STABLE || state == State.MUTATING; }
}
