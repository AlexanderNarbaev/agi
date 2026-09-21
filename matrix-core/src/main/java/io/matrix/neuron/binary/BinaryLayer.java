package io.matrix.neuron.binary;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A layer of {@link BinaryNeuron}s implementing forward/backward pass
 * with local binary error signals.
 *
 * <p>Implements the multi-layer binary network layer from arXiv:2412.00119.
 * Each neuron independently computes its output and updates its weights
 * using only local error signals — no backpropagation required.
 *
 * <h3>Forward pass</h3>
 * <p>Each neuron applies XNOR + Popcount to its slice of the input,
 * producing a single binary output. The layer output is the concatenation
 * of all neuron outputs.
 *
 * <h3>Backward pass (local error signal)</h3>
 * <p>Each neuron receives a local error signal (target - actual) and
 * updates its hidden weights independently. No gradient flows between layers.
 *
 * <p>Thread-safe for concurrent reads. Training methods are NOT thread-safe.
 */
public final class BinaryLayer {

    private final List<BinaryNeuron> neurons;
    private final int inputSize;
    private final int outputSize;

    /**
     * Creates a layer with random neurons.
     *
     * @param inputSize  number of input bits per neuron
     * @param outputSize number of neurons (output bits)
     * @param rng        random generator
     */
    public BinaryLayer(int inputSize, int outputSize, Random rng) {
        if (inputSize < 1) {
            throw new IllegalArgumentException("inputSize must be >= 1, got: " + inputSize);
        }
        if (outputSize < 1) {
            throw new IllegalArgumentException("outputSize must be >= 1, got: " + outputSize);
        }
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        List<BinaryNeuron> list = new ArrayList<>(outputSize);
        for (int i = 0; i < outputSize; i++) {
            list.add(new BinaryNeuron(inputSize, rng));
        }
        this.neurons = Collections.unmodifiableList(list);
    }

    /**
     * Creates a layer from pre-built neurons.
     *
     * @param neurons list of neurons (defensively copied)
     */
    public BinaryLayer(List<BinaryNeuron> neurons) {
        if (neurons.isEmpty()) {
            throw new IllegalArgumentException("At least one neuron required");
        }
        this.inputSize = neurons.get(0).inputSize();
        this.outputSize = neurons.size();
        for (int i = 1; i < neurons.size(); i++) {
            if (neurons.get(i).inputSize() != inputSize) {
                throw new IllegalArgumentException(
                        "All neurons must have the same inputSize. Neuron 0 has "
                                + inputSize + ", neuron " + i + " has " + neurons.get(i).inputSize());
            }
        }
        this.neurons = Collections.unmodifiableList(new ArrayList<>(neurons));
    }

    // ─── Forward pass ───

    /**
     * Computes the layer output for the given binary input.
     *
     * <p>Each neuron processes the full input (not sliced) and produces
     * one output bit. The output BitSet has {@code outputSize} bits.
     *
     * @param input binary input vector of size {@code inputSize}
     * @return binary output vector of size {@code outputSize}
     */
    public BitSet forward(BitSet input) {
        BitSet output = new BitSet(outputSize);
        for (int i = 0; i < outputSize; i++) {
            if (neurons.get(i).forward(input) == 1) {
                output.set(i);
            }
        }
        return output;
    }

    // ─── Training ───

    /**
     * Trains the layer on a single sample using local error signals.
     *
     * <p>Each neuron independently computes its error and updates its weights.
     * Returns the error signal vector for upstream layers (if any).
     *
     * @param input  binary input vector
     * @param target desired output vector (size = outputSize)
     * @return error signal vector: {@code target[i] - output[i]} for each neuron
     */
    public int[] trainStep(BitSet input, BitSet target) {
        int[] errors = new int[outputSize];
        for (int i = 0; i < outputSize; i++) {
            int t = target.get(i) ? 1 : 0;
            errors[i] = neurons.get(i).trainStep(input, t);
        }
        return errors;
    }

    /**
     * Computes the layer output and error without updating weights (inference).
     *
     * @param input  binary input
     * @param target desired output
     * @return error vector
     */
    public int[] computeErrors(BitSet input, BitSet target) {
        int[] errors = new int[outputSize];
        for (int i = 0; i < outputSize; i++) {
            int output = neurons.get(i).forward(input);
            int t = target.get(i) ? 1 : 0;
            errors[i] = t - output;
        }
        return errors;
    }

    /**
     * Computes a binary error summary from the error vector.
     *
     * <p>Returns a BitSet where bit i is set if neuron i has non-zero error.
     * Useful for propagating error signals to upstream layers.
     *
     * @param errors error vector from {@link #trainStep} or {@link #computeErrors}
     * @return BitSet with bits set where error != 0
     */
    public BitSet errorToBitSet(int[] errors) {
        BitSet result = new BitSet(errors.length);
        for (int i = 0; i < errors.length; i++) {
            if (errors[i] != 0) {
                result.set(i);
            }
        }
        return result;
    }

    // ─── Accessors ───

    /** Returns an unmodifiable view of the neurons. */
    public List<BinaryNeuron> neurons() {
        return neurons;
    }

    /** Number of input bits per neuron. */
    public int inputSize() {
        return inputSize;
    }

    /** Number of neurons (= output bits). */
    public int outputSize() {
        return outputSize;
    }

    /**
     * Computes the total number of binary weight bits in this layer.
     */
    public long totalWeightBits() {
        return (long) inputSize * outputSize;
    }

    /**
     * Computes the total memory footprint in bytes (hidden int weights + binary weights).
     */
    public long memoryBytes() {
        // 4 bytes per hidden weight (int) + 1 bit per binary weight
        long hiddenBytes = (long) inputSize * outputSize * 4;
        long binaryBytes = ((long) inputSize * outputSize + 7) / 8;
        return hiddenBytes + binaryBytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryLayer that)) return false;
        return inputSize == that.inputSize
                && outputSize == that.outputSize
                && neurons.equals(that.neurons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(neurons, inputSize, outputSize);
    }

    @Override
    public String toString() {
        return "BinaryLayer{inputSize=" + inputSize + ", outputSize=" + outputSize + "}";
    }
}
