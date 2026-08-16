package io.matrix.reasoning;

import io.matrix.neuron.NeuronLayer;

import java.util.BitSet;
import java.util.Objects;

/**
 * A single step in a Boolean Reasoning Chain (BRC).
 *
 * <p>Each step applies a NeuronLayer to the current state,
 * producing a new boolean vector and checking for convergence.
 *
 * <p>Steps are composable: output of step N feeds into step N+1.
 */
public final class BrcStep {

    private final NeuronLayer layer;
    private final String name;
    private final int convergenceThreshold;

    /**
     * Creates a BRC step.
     *
     * @param layer neuron layer to apply
     * @param name human-readable step name
     * @param convergenceThreshold Hamming distance threshold for convergence (0 = exact match)
     */
    public BrcStep(NeuronLayer layer, String name, int convergenceThreshold) {
        this.layer = Objects.requireNonNull(layer, "layer");
        this.name = Objects.requireNonNull(name, "name");
        this.convergenceThreshold = Math.max(0, convergenceThreshold);
    }

    /**
     * Applies this step to the given state.
     *
     * @param state current BRC state
     * @return new state after applying this step
     */
    public BrcState apply(BrcState state) {
        BitSet input = padInput(state.vector(), requiredInputWidth());
        BitSet output = layer.evaluate(input);
        boolean converged = checkConvergence(state, output);
        return state.next(output, converged);
    }

    /**
     * Returns the required input width for this step's layer.
     */
    public int requiredInputWidth() {
        return layer.outputWidth() * layer.k();
    }

    /**
     * Returns the output width of this step.
     */
    public int outputWidth() {
        return layer.outputWidth();
    }

    /**
     * Returns the step name.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the convergence threshold.
     */
    public int convergenceThreshold() {
        return convergenceThreshold;
    }

    /**
     * Checks if the output has converged relative to the input.
     *
     * <p>Convergence means the Hamming distance between input and output
     * is at or below the threshold.
     */
    private boolean checkConvergence(BrcState state, BitSet output) {
        if (convergenceThreshold == 0) {
            return state.vector().equals(output);
        }
        int distance = BrcState.hammingDistance(state.vector(), output);
        return distance <= convergenceThreshold;
    }

    /**
     * Pads input to required width by repeating pattern.
     */
    private BitSet padInput(BitSet input, int requiredWidth) {
        if (input.length() >= requiredWidth) {
            return input.get(0, requiredWidth);
        }
        BitSet padded = new BitSet(requiredWidth);
        int srcLen = Math.max(1, input.length());
        for (int i = 0; i < requiredWidth; i++) {
            if (input.get(i % srcLen)) {
                padded.set(i);
            }
        }
        return padded;
    }

    @Override
    public String toString() {
        return "BrcStep{name='%s', in=%d, out=%d, threshold=%d}".formatted(
            name, requiredInputWidth(), outputWidth(), convergenceThreshold
        );
    }
}
