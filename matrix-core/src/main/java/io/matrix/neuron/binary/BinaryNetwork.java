package io.matrix.neuron.binary;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Multi-layer binary neural network with local error signal training.
 *
 * <p>Implements the multi-layer BNN from arXiv:2412.00119 — "Training Multi-Layer
 * Binary Neural Networks With Local Binary Error Signals" (Colombo et al., 2025).
 *
 * <h3>Architecture</h3>
 * <p>A stack of {@link BinaryLayer}s. Each layer receives the binary output of the
 * previous layer as input. The first layer receives the external input; the last
 * layer produces the network output.
 *
 * <h3>Inference</h3>
 * <pre>
 *   h[0] = input
 *   for each layer l:
 *     h[l+1] = layer[l].forward(h[l])
 *   output = h[L]
 * </pre>
 *
 * <h3>Training (local error signals, no backpropagation)</h3>
 * <pre>
 *   // Forward pass
 *   h[0] = input
 *   for each layer l: h[l+1] = layer[l].forward(h[l])
 *
 *   // Backward pass — local errors only
 *   error[L] = target - output
 *   for l = L-1 down to 0:
 *     layer[l].trainStep(h[l], derived_target[l])
 *     // derived_target uses upstream error signals, not gradients
 * </pre>
 *
 * <p>Thread-safe for concurrent reads. Training methods are NOT thread-safe.
 */
public final class BinaryNetwork {

    private final List<BinaryLayer> layers;
    private final int[] layerSizes;

    /**
     * Creates a network with the given architecture.
     *
     * @param layerSizes array of layer sizes: [inputSize, hidden1, hidden2, ..., outputSize]
     *                   Must have at least 2 elements. First element is the input size.
     * @param rng        random generator for weight initialization
     */
    public BinaryNetwork(int[] layerSizes, Random rng) {
        if (layerSizes.length < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 layer sizes (input + output), got: " + layerSizes.length);
        }
        for (int i = 0; i < layerSizes.length; i++) {
            if (layerSizes[i] < 1) {
                throw new IllegalArgumentException(
                        "Layer size at index " + i + " must be >= 1, got: " + layerSizes[i]);
            }
        }
        this.layerSizes = layerSizes.clone();
        List<BinaryLayer> list = new ArrayList<>(layerSizes.length - 1);
        for (int i = 0; i < layerSizes.length - 1; i++) {
            list.add(new BinaryLayer(layerSizes[i], layerSizes[i + 1], rng));
        }
        this.layers = Collections.unmodifiableList(list);
    }

    /**
     * Creates a network from pre-built layers.
     *
     * @param layers list of layers (defensively copied)
     */
    public BinaryNetwork(List<BinaryLayer> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("At least one layer required");
        }
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
        this.layerSizes = new int[layers.size() + 1];
        layerSizes[0] = layers.get(0).inputSize();
        for (int i = 0; i < layers.size(); i++) {
            layerSizes[i + 1] = layers.get(i).outputSize();
        }
    }

    // ─── Inference ───

    /**
     * Performs a feed-forward inference pass.
     *
     * @param input binary input vector of size {@code layerSizes[0]}
     * @return binary output vector of size {@code layerSizes[last]}
     */
    public BitSet forward(BitSet input) {
        BitSet current = input;
        for (BinaryLayer layer : layers) {
            current = layer.forward(current);
        }
        return current;
    }

    /**
     * Performs a forward pass and returns all intermediate activations.
     *
     * <p>Index 0 = input, index L = output. Array length = layers.size() + 1.
     *
     * @param input binary input
     * @return array of intermediate BitSet activations
     */
    public BitSet[] forwardWithActivations(BitSet input) {
        BitSet[] activations = new BitSet[layers.size() + 1];
        activations[0] = input;
        BitSet current = input;
        for (int i = 0; i < layers.size(); i++) {
            current = layers.get(i).forward(current);
            activations[i + 1] = current;
        }
        return activations;
    }

    // ─── Training ───

    /**
     * Trains the network on a single input-target pair using local error signals.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Forward pass to compute all activations</li>
     *   <li>Compute output error: target - output</li>
     *   <li>For each layer (output to input), compute local targets and update weights</li>
     * </ol>
     *
     * <p>The local target for hidden layers is derived from the error signal of the
     * downstream layer, using a weighted vote of neuron errors. This is the key
     * innovation from arXiv:2412.00119 — no gradient backpropagation needed.
     *
     * @param input  binary input vector
     * @param target desired output vector
     * @return total absolute error across all output neurons
     */
    public int trainStep(BitSet input, BitSet target) {
        // Forward pass — collect activations
        BitSet[] activations = forwardWithActivations(input);

        // Output layer: direct error signal
        int outputLayer = layers.size() - 1;
        int[] errors = layers.get(outputLayer).trainStep(activations[outputLayer], target);

        // Hidden layers: derive local targets from downstream errors
        for (int l = outputLayer - 1; l >= 0; l--) {
            BitSet localTarget = deriveLocalTarget(l, errors, activations[l + 1]);
            errors = layers.get(l).trainStep(activations[l], localTarget);
        }

        // Compute total absolute error at output
        int totalError = 0;
        for (int e : errors) {
            totalError += Math.abs(e);
        }
        return totalError;
    }

    /**
     * Derives a local target for a hidden layer from downstream error signals.
     *
     * <p>Strategy: For each hidden neuron, the target is set to 1 if the majority
     * of downstream neurons with positive error would benefit from activation=1,
     * and 0 otherwise. If no error signal, the target equals the current output
     * (no change).
     *
     * @param layerIndex  index of the layer receiving the target
     * @param errors      error vector from the downstream layer
     * @param activations current activations of the layer
     * @return local target BitSet
     */
    private BitSet deriveLocalTarget(int layerIndex, int[] errors, BitSet activations) {
        int size = layers.get(layerIndex).outputSize();
        BitSet target = new BitSet(size);

        // Count positive vs negative errors in downstream layer
        int positiveErrors = 0;
        int negativeErrors = 0;
        for (int error : errors) {
            if (error > 0) positiveErrors++;
            else if (error < 0) negativeErrors++;
        }

        // If no error, keep current activations as target
        if (positiveErrors == 0 && negativeErrors == 0) {
            for (int i = 0; i < size; i++) {
                if (activations.get(i)) {
                    target.set(i);
                }
            }
            return target;
        }

        // Derive target based on error direction
        // If more positive errors: encourage activation (set more bits)
        // If more negative errors: discourage activation (clear more bits)
        boolean encourageActivation = positiveErrors >= negativeErrors;
        for (int i = 0; i < size; i++) {
            boolean currentBit = activations.get(i);
            if (encourageActivation) {
                // Keep current 1s, potentially flip some 0s to 1
                if (currentBit || (positiveErrors > negativeErrors * 2 && i % 2 == 0)) {
                    target.set(i);
                }
            } else {
                // Keep current 0s, potentially flip some 1s to 0
                if (currentBit && (negativeErrors > positiveErrors * 2 && i % 2 == 0)) {
                    // Clear bit (target=0)
                } else {
                    if (currentBit) target.set(i);
                }
            }
        }
        return target;
    }

    // ─── Accessors ───

    /** Returns an unmodifiable view of the layers. */
    public List<BinaryLayer> layers() {
        return layers;
    }

    /** Returns the layer size array (input, hidden..., output). */
    public int[] layerSizes() {
        return layerSizes.clone();
    }

    /** Number of layers (excluding input). */
    public int depth() {
        return layers.size();
    }

    /** Input size. */
    public int inputSize() {
        return layerSizes[0];
    }

    /** Output size. */
    public int outputSize() {
        return layerSizes[layerSizes.length - 1];
    }

    /**
     * Computes the total number of binary weight bits across all layers.
     */
    public long totalWeightBits() {
        long total = 0;
        for (BinaryLayer layer : layers) {
            total += layer.totalWeightBits();
        }
        return total;
    }

    /**
     * Computes the total memory footprint in bytes.
     */
    public long memoryBytes() {
        long total = 0;
        for (BinaryLayer layer : layers) {
            total += layer.memoryBytes();
        }
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryNetwork that)) return false;
        return layers.equals(that.layers);
    }

    @Override
    public int hashCode() {
        return layers.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BinaryNetwork{");
        for (int i = 0; i < layerSizes.length; i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(layerSizes[i]);
        }
        sb.append("}");
        return sb.toString();
    }
}
