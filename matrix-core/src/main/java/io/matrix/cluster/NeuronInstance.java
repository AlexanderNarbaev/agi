package io.matrix.cluster;

import io.matrix.neuron.TruthTable;

import java.util.Objects;

/**
 * Wraps a TruthTable with runtime state for the cluster actor.
 */
public final class NeuronInstance {

    public enum State { STABLE, LEARNING, MUTATING, FROZEN }

    private final NeuronId id;
    private final TruthTable truthTable;
    private final State state;
    private volatile boolean lastOutput;

    public NeuronInstance(NeuronId id, TruthTable truthTable, State state) {
        this.id = Objects.requireNonNull(id);
        this.truthTable = Objects.requireNonNull(truthTable);
        this.state = Objects.requireNonNull(state);
    }

    public static NeuronInstance stable(NeuronId id, TruthTable table) {
        return new NeuronInstance(id, table, State.STABLE);
    }

    public static NeuronInstance frozen(NeuronId id, TruthTable table) {
        return new NeuronInstance(id, table, State.FROZEN);
    }

    public NeuronId id() { return id; }

    public TruthTable truthTable() { return truthTable; }

    public State state() { return state; }

    public int k() { return truthTable.k(); }

    public boolean lastOutput() { return lastOutput; }

    public void setLastOutput(boolean value) { this.lastOutput = value; }

    public boolean isFrozen() { return state == State.FROZEN; }

    public boolean isMutable() { return state == State.STABLE || state == State.MUTATING; }
}
