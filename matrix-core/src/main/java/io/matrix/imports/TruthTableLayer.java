package io.matrix.imports;

import io.matrix.neuron.TruthTable;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * Minimal adapter: a "layer" of {@link TruthTable} neurons that takes a
 * flat {@code BitSet} input, dispatches each neuron's {@code k}-bit
 * slice, and concatenates the outputs.
 *
 * <p>Used by W8 to compose projected LLM weights into a multi-layer
 * {@link io.matrix.reasoning.BrcStep}. Bypasses the {@code DecisionTree}
 * adapter path because the projection produces {@code TruthTable}s
 * directly.
 */
public final class TruthTableLayer {

    private final List<TruthTable> neurons;
    private final int k;

    public TruthTableLayer(List<TruthTable> neurons, int k) {
        this.neurons = Objects.requireNonNull(neurons, "neurons");
        if (neurons.isEmpty()) throw new IllegalArgumentException("empty neurons");
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        this.k = k;
    }

    public int neuronCount() { return neurons.size(); }
    public int k() { return k; }
    public int outputWidth() { return neurons.size(); }
    public int inputWidth() { return neurons.size() * k; }
    /** Public read-only access to the underlying neuron list (Phase 3 training). */
    public List<TruthTable> neurons() { return neurons; }

    /**
     * Replace the neuron at {@code index} with a freshly-trained
     * {@link TruthTable}. Triggers a re-export of the native tables
     * on the next native evaluation by invalidating the cached
     * export. Returns the previously-held neuron for diff/debug.
     *
     * <p>Used by {@link io.matrix.api.ChainTrainerEndpoint} to write
     * trained weights back into the live chain so subsequent
     * evaluations reflect the training.
     */
    public TruthTable replaceNeuron(int index, TruthTable fresh) {
        if (index < 0 || index >= neurons.size()) {
            throw new IndexOutOfBoundsException("neuron index " + index);
        }
        TruthTable prev = neurons.get(index);
        neurons.set(index, Objects.requireNonNull(fresh, "fresh"));
        return prev;
    }

    /**
     * Export the packed truth tables for native evaluation.
     * Each entry is a {@code long[]} from {@link TruthTable#table().toLongArray()}.
     * Used by {@link BooleanChainRunner#evaluateNative} via Project Panama.
     */
    public List<long[]> exportTablesForNative() {
        return neurons.stream()
                .map(tt -> tt.table().toLongArray())
                .toList();
    }

    /**
     * Evaluate the layer on the input. The input is sliced into
     * {@code neuronCount} chunks of {@code k} bits; each chunk is fed
     * to its neuron; the bits are concatenated into the output.
     *
     * <p>If the input is shorter than {@code inputWidth()}, it is
     * zero-padded on the right.
     */
    public BitSet evaluate(BitSet input) {
        BitSet out = new BitSet(neuronCount());
        for (int i = 0; i < neurons.size(); i++) {
            TruthTable neuron = neurons.get(i);
            int sliceStart = i * k;
            int cellIndex = 0;
            for (int j = 0; j < k; j++) {
                if (input.get(sliceStart + j)) {
                    cellIndex |= (1 << j);
                }
            }
            boolean bit = neuron.evaluate(cellIndex);
            if (bit) out.set(i);
        }
        return out;
    }

    /** Count bits set in the given BitSet (helper for magnitude scoring). */
    public static int bitSetCardinality(BitSet bs) {
        int n = 0;
        for (int i = 0; i < bs.length(); i++) if (bs.get(i)) n++;
        return n;
    }
}