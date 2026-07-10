package io.matrix.reasoning;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * State of a Boolean Reasoning Chain (BRC).
 *
 * <p>Encapsulates the current boolean vector, intermediate results,
 * and metadata for multi-step logical reasoning.
 *
 * <p>Immutable: each step creates a new BrcState.
 */
public final class BrcState {

    private final BitSet vector;
    private final int stepIndex;
    private final List<BitSet> history;
    private final boolean converged;

    /**
     * Creates initial state from input vector.
     */
    public BrcState(BitSet input, int vectorWidth) {
        this.vector = copyAndResize(input, vectorWidth);
        this.stepIndex = 0;
        this.history = new ArrayList<>();
        this.converged = false;
    }

    /**
     * Private constructor for state transitions.
     */
    private BrcState(BitSet vector, int stepIndex, List<BitSet> history, boolean converged) {
        this.vector = vector;
        this.stepIndex = stepIndex;
        this.history = history;
        this.converged = converged;
    }

    /**
     * Creates next state after a reasoning step.
     *
     * @param output output vector from the step
     * @param converged whether the chain has converged
     * @return new state with updated history
     */
    public BrcState next(BitSet output, boolean converged) {
        List<BitSet> newHistory = new ArrayList<>(history);
        newHistory.add(copyAndResize(vector, vector.length()));
        return new BrcState(
            copyAndResize(output, output.length()),
            stepIndex + 1,
            newHistory,
            converged
        );
    }

    /**
     * Returns the current boolean vector.
     */
    public BitSet vector() {
        return copyAndResize(vector, vector.length());
    }

    /**
     * Returns the current step index (0-based).
     */
    public int stepIndex() {
        return stepIndex;
    }

    /**
     * Returns the history of intermediate vectors.
     */
    public List<BitSet> history() {
        return List.copyOf(history);
    }

    /**
     * Returns whether the chain has converged.
     */
    public boolean isConverged() {
        return converged;
    }

    /**
     * Returns the vector width (number of bits).
     */
    public int vectorWidth() {
        return vector.length();
    }

    /**
     * Computes Hamming distance between current and previous state.
     *
     * @return Hamming distance, or -1 if no previous state
     */
    public int hammingDistanceToPrevious() {
        if (history.isEmpty()) {
            return -1;
        }
        BitSet previous = history.get(history.size() - 1);
        return hammingDistance(vector, previous);
    }

    /**
     * Computes Hamming distance between two bit sets.
     */
    public static int hammingDistance(BitSet a, BitSet b) {
        BitSet xor = (BitSet) a.clone();
        xor.xor(b);
        return xor.cardinality();
    }

    /**
     * Creates a copy of the bit set with specified width.
     */
    private static BitSet copyAndResize(BitSet source, int width) {
        BitSet copy = new BitSet(width);
        for (int i = 0; i < Math.min(source.length(), width); i++) {
            if (source.get(i)) {
                copy.set(i);
            }
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrcState other)) return false;
        return stepIndex == other.stepIndex
            && converged == other.converged
            && Objects.equals(vector, other.vector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vector, stepIndex, converged);
    }

    @Override
    public String toString() {
        return "BrcState{step=%d, converged=%s, width=%d, hamming=%d}".formatted(
            stepIndex, converged, vector.length(), hammingDistanceToPrevious()
        );
    }
}
