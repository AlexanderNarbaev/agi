package io.matrix.neuron.binary;

import java.util.BitSet;
import java.util.Objects;
import java.util.Random;

/**
 * Binary neuron with XNOR-based activation and local error signal training.
 *
 * <p>Implements the neuron model from arXiv:2412.00119 — "Training Multi-Layer
 * Binary Neural Networks With Local Binary Error Signals" (Colombo et al., 2025).
 *
 * <h3>Forward pass</h3>
 * <ol>
 *   <li>Compute XNOR between binary input and binary weights</li>
 *   <li>Popcount the XNOR result to get activation</li>
 *   <li>Output = 1 if popcount &ge; threshold, else 0</li>
 * </ol>
 *
 * <h3>Training (local binary error signal)</h3>
 * <ol>
 *   <li>Compute local error: {@code e = target - output} (ternary: -1, 0, +1)</li>
 *   <li>Update hidden integer weights: {@code h[i] += e * (2*input[i] - 1)}</li>
 *   <li>Binarize: {@code w[i] = sign(h[i])} (positive &rarr; 1, non-positive &rarr; 0)</li>
 * </ol>
 *
 * <p>Thread-safe: all mutable state is confined to the instance; concurrent
 * reads of immutable snapshots are safe. Training methods are NOT thread-safe
 * (caller must synchronize externally if needed).
 *
 * @param inputSize number of input bits
 * @param threshold popcount threshold for activation (default: ceil(inputSize / 2))
 */
public final class BinaryNeuron {

    private final int inputSize;
    private final int threshold;

    /**
     * Hidden integer weights for metaplasticity.
     * {@code hiddenWeights[i]} is an unbounded integer; binary weight is derived
     * as {@code sign(hiddenWeights[i])}.
     */
    private final int[] hiddenWeights;

    /**
     * Cached binary weights derived from hidden weights.
     * Bit {@code i} is set iff {@code hiddenWeights[i] > 0}.
     */
    private final BitSet binaryWeights;

    /**
     * Creates a neuron with random binary weights.
     *
     * @param inputSize number of input bits, must be &ge; 1
     * @param threshold popcount threshold, must be in [0, inputSize]
     * @param rng       random generator for weight initialization
     */
    public BinaryNeuron(int inputSize, int threshold, Random rng) {
        if (inputSize < 1) {
            throw new IllegalArgumentException("inputSize must be >= 1, got: " + inputSize);
        }
        if (threshold < 0 || threshold > inputSize) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, " + inputSize + "], got: " + threshold);
        }
        this.inputSize = inputSize;
        this.threshold = threshold;
        this.hiddenWeights = new int[inputSize];
        this.binaryWeights = new BitSet(inputSize);
        initializeWeights(rng);
    }

    /**
     * Creates a neuron with threshold = ceil(inputSize / 2).
     */
    public BinaryNeuron(int inputSize, Random rng) {
        this(inputSize, (inputSize + 1) / 2, rng);
    }

    /**
     * Creates a neuron with explicit hidden weights (for deserialization / testing).
     *
     * @param inputSize     number of input bits
     * @param threshold     popcount threshold
     * @param hiddenWeights initial hidden weight values (copied)
     */
    public BinaryNeuron(int inputSize, int threshold, int[] hiddenWeights) {
        if (inputSize < 1) {
            throw new IllegalArgumentException("inputSize must be >= 1, got: " + inputSize);
        }
        if (threshold < 0 || threshold > inputSize) {
            throw new IllegalArgumentException(
                    "threshold must be in [0, " + inputSize + "], got: " + threshold);
        }
        if (hiddenWeights.length != inputSize) {
            throw new IllegalArgumentException(
                    "hiddenWeights length " + hiddenWeights.length + " must equal inputSize=" + inputSize);
        }
        this.inputSize = inputSize;
        this.threshold = threshold;
        this.hiddenWeights = hiddenWeights.clone();
        this.binaryWeights = new BitSet(inputSize);
        rebuildBinaryWeights();
    }

    // ─── Forward pass ───

    /**
     * Computes the neuron output for the given binary input.
     *
     * <p>Algorithm:
     * <pre>
     *   xnor[i] = NOT(input[i] XOR weight[i])   // 1 if bits match
     *   activation = popcount(xnor)
     *   output = (activation >= threshold) ? 1 : 0
     * </pre>
     *
     * @param input binary input vector (bit i = i-th input)
     * @return 1 if activated, 0 otherwise
     */
    public int forward(BitSet input) {
        int activation = computeActivation(input);
        return activation >= threshold ? 1 : 0;
    }

    /**
     * Computes the raw activation (popcount of XNOR) without thresholding.
     *
     * @param input binary input vector
     * @return popcount of XNOR(input, weights), in [0, inputSize]
     */
    public int computeActivation(BitSet input) {
        int count = 0;
        for (int i = 0; i < inputSize; i++) {
            boolean inputBit = input.get(i);
            boolean weightBit = binaryWeights.get(i);
            // XNOR: true iff bits are equal
            if (inputBit == weightBit) {
                count++;
            }
        }
        return count;
    }

    // ─── Training ───

    /**
     * Performs a local weight update using a binary error signal.
     *
     * <p>Algorithm (from arXiv:2412.00119 §3):
     * <pre>
     *   error = target - output  (ternary: -1, 0, +1)
     *   for each input bit i:
     *     direction = 2 * input[i] - 1   (+1 if input=1, -1 if input=0)
     *     hiddenWeights[i] += error * direction
     *     binaryWeights[i] = sign(hiddenWeights[i])
     * </pre>
     *
     * @param input  binary input vector
     * @param target desired output (0 or 1)
     * @return the error signal (-1, 0, or +1)
     */
    public int trainStep(BitSet input, int target) {
        int output = forward(input);
        int error = target - output;
        if (error != 0) {
            updateWeights(input, error);
        }
        return error;
    }

    /**
     * Updates hidden and binary weights based on error signal and input.
     *
     * <p>For each input bit:
     * <ul>
     *   <li>If input[i]=1 and error=+1: increment hidden weight (strengthen connection)</li>
     *   <li>If input[i]=0 and error=+1: decrement hidden weight (weaken connection)</li>
     *   <li>Opposite for error=-1</li>
     * </ul>
     *
     * @param input binary input
     * @param error error signal (-1 or +1)
     */
    private void updateWeights(BitSet input, int error) {
        for (int i = 0; i < inputSize; i++) {
            // direction = +1 if input[i]=1, -1 if input[i]=0
            int direction = input.get(i) ? 1 : -1;
            hiddenWeights[i] += error * direction;
            // Binarize: positive → 1 (set), non-positive → 0 (clear)
            if (hiddenWeights[i] > 0) {
                binaryWeights.set(i);
            } else {
                binaryWeights.clear(i);
            }
        }
    }

    // ─── Accessors ───

    /** Returns the number of input bits. */
    public int inputSize() {
        return inputSize;
    }

    /** Returns the popcount threshold. */
    public int threshold() {
        return threshold;
    }

    /**
     * Returns a snapshot of the binary weights.
     * Modifications to the returned BitSet do not affect this neuron.
     */
    public BitSet binaryWeights() {
        return (BitSet) binaryWeights.clone();
    }

    /**
     * Returns a copy of the hidden integer weights.
     */
    public int[] hiddenWeights() {
        return hiddenWeights.clone();
    }

    /**
     * Returns the count of binary weight bits set to 1.
     */
    public int weightPopcount() {
        return binaryWeights.cardinality();
    }

    // ─── TruthTable integration ───

    /**
     * Evaluates this neuron as a Boolean function on the given integer input.
     *
     * <p>Compatible with the {@link io.matrix.neuron.TruthTable} evaluation
     * contract: input bits are packed LSB-first.
     *
     * @param input integer whose low {@code inputSize} bits encode the input vector
     * @return true if neuron activates, false otherwise
     */
    public boolean evaluate(int input) {
        int count = 0;
        for (int i = 0; i < inputSize; i++) {
            boolean inputBit = ((input >>> i) & 1) == 1;
            boolean weightBit = binaryWeights.get(i);
            if (inputBit == weightBit) {
                count++;
            }
        }
        return count >= threshold;
    }

    // ─── Internal ───

    private void initializeWeights(Random rng) {
        for (int i = 0; i < inputSize; i++) {
            boolean bit = rng.nextBoolean();
            // Hidden weights: +1 for binary 1, 0 for binary 0
            hiddenWeights[i] = bit ? 1 : 0;
            if (bit) {
                binaryWeights.set(i);
            }
        }
    }

    private void rebuildBinaryWeights() {
        binaryWeights.clear();
        for (int i = 0; i < inputSize; i++) {
            if (hiddenWeights[i] > 0) {
                binaryWeights.set(i);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryNeuron that)) return false;
        return inputSize == that.inputSize
                && threshold == that.threshold
                && binaryWeights.equals(that.binaryWeights);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputSize, threshold, binaryWeights);
    }

    @Override
    public String toString() {
        return "BinaryNeuron{inputSize=" + inputSize
                + ", threshold=" + threshold
                + ", weightPopcount=" + weightPopcount() + "}";
    }
}
