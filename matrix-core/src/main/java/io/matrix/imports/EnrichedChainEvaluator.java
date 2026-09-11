package io.matrix.imports;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;

import java.util.ArrayList;
import java.util.List;

/**
 * DESIGN-20 — Wraps a {@link BooleanChainRunner} and produces
 * {@link ChainEnrichedOutput}: per-layer, per-neuron magnitude,
 * chemical vector, and neurotransmitter tag.
 *
 * <p>Pure function (CONSTITUTION I): same chain + same input →
 * same enriched output. No Random, no wall-clock.
 *
 * <p>Backward-compatible with {@link BooleanChainRunner#evaluate}
 * which still returns plain boolean[] for legacy callers.
 */
public final class EnrichedChainEvaluator {

    private final BooleanChainRunner runner;

    public EnrichedChainEvaluator(BooleanChainRunner runner) {
        if (runner == null) throw new IllegalArgumentException("runner");
        this.runner = runner;
    }

    /**
     * Run the chain forward and produce enriched output.
     *
     * @param input bits fed into the chain (length ≥ 1)
     * @return ChainEnrichedOutput with bits + per-layer enriched data
     */
    public ChainEnrichedOutput evaluateEnriched(boolean[] input) {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("input must be non-empty");
        }
        // First, run the plain forward pass to get the bits
        boolean[] bits = runner.evaluate(input);
        // Then, derive enriched data from each layer's neurons
        var layers = runner.layers();
        int L = layers.size();
        double[][] mag = new double[L][];
        double[][][] chem = new double[L][][];
        Neurotransmitter[][] tags = new Neurotransmitter[L][];

        // For chemicalVector we need each neuron's k-bit slice.
        // Use the max k across the layer to size the layer input; each
        // neuron reads its own k from its truth table.
        for (int li = 0; li < L; li++) {
            var layer = layers.get(li);
            int n = layer.neuronCount();
            int maxK = layer.k();

            // Effective layer input (padded/truncated to n * maxK)
            int inputWidth = n * maxK;
            boolean[] layerInput = new boolean[inputWidth];
            int limit = Math.min(input.length, inputWidth);
            for (int i = 0; i < limit; i++) layerInput[i] = input[i];

            mag[li] = new double[n];
            chem[li] = new double[n][];
            tags[li] = new Neurotransmitter[n];

            for (int ni = 0; ni < n; ni++) {
                TruthTable t = layer.neurons().get(ni);
                if (t == null) continue;
                int neuronK = t.k();
                // Per-neuron slice (neuronK bits)
                boolean[] neuronInput = new boolean[neuronK];
                int sliceStart = ni * maxK;
                for (int j = 0; j < neuronK; j++) {
                    int idx = sliceStart + j;
                    if (idx < layerInput.length) neuronInput[j] = layerInput[idx];
                }
                EnrichedNeuron enriched = EnrichedNeuron.derive(t, neuronInput);
                mag[li][ni] = enriched.magnitude();
                chem[li][ni] = enriched.chemicalVector();
                tags[li][ni] = enriched.tag();
            }
        }
        return new ChainEnrichedOutput(bits, mag, chem, tags);
    }
}
