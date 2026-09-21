package io.matrix.imports;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;

/**
 * DESIGN-20 — Enriched forward-pass output for a BooleanChainRunner.
 *
 * <p>Carries per-layer, per-neuron magnitude, chemicalVector, and tag,
 * alongside the boolean bits. Pure data record — no methods, no
 * mutation. Backward-compatible consumers can ignore the enriched
 * fields and just use {@link #bits}.
 *
 * <p>Cost: O(layers × neurons × 4) additional operations per forward
 * pass (chemicalVector is 4D). For Qwen2.5-0.5B at 24 layers × ~915
 * neurons ≈ 88,000 ops; < 1 ms on RTX 5070, < 10 ms on CPU.
 */
public record ChainEnrichedOutput(
        boolean[] bits,
        double[][] magnitudePerLayer,
        double[][][] chemicalPerLayer,
        Neurotransmitter[][] tagsPerLayer
) {
    public ChainEnrichedOutput {
        if (bits == null) throw new IllegalArgumentException("bits must not be null");
        if (magnitudePerLayer == null) throw new IllegalArgumentException("magnitudePerLayer");
        if (chemicalPerLayer == null) throw new IllegalArgumentException("chemicalPerLayer");
        if (tagsPerLayer == null) throw new IllegalArgumentException("tagsPerLayer");
        // Cross-validation: layers × neurons consistent
        if (magnitudePerLayer.length != chemicalPerLayer.length
                || chemicalPerLayer.length != tagsPerLayer.length) {
            throw new IllegalArgumentException("layer count mismatch");
        }
        for (int li = 0; li < magnitudePerLayer.length; li++) {
            if (magnitudePerLayer[li].length != chemicalPerLayer[li].length
                    || chemicalPerLayer[li].length != tagsPerLayer[li].length) {
                throw new IllegalArgumentException(
                        "neuron count mismatch at layer " + li);
            }
            for (int ni = 0; ni < chemicalPerLayer[li].length; ni++) {
                if (chemicalPerLayer[li][ni].length != EnrichedNeuron.CHEMICAL_DIM) {
                    throw new IllegalArgumentException(
                            "chemical dim must be " + EnrichedNeuron.CHEMICAL_DIM
                                    + " at layer " + li + " neuron " + ni);
                }
            }
        }
    }

    /** Layer count (convenience). */
    public int layerCount() { return magnitudePerLayer.length; }

    /** Total neurons across all layers (convenience). */
    public int totalNeurons() {
        int sum = 0;
        for (double[] layer : magnitudePerLayer) sum += layer.length;
        return sum;
    }

    /** Mean magnitude across all layers and neurons. */
    public double meanMagnitude() {
        long count = 0;
        double sum = 0;
        for (double[] layer : magnitudePerLayer) {
            for (double m : layer) {
                sum += m;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    /** Mean of a specific chemical dimension across all layers × neurons. */
    public double meanChemical(int dim) {
        if (dim < 0 || dim >= EnrichedNeuron.CHEMICAL_DIM) {
            throw new IllegalArgumentException("dim out of range: " + dim);
        }
        long count = 0;
        double sum = 0;
        for (double[][] layer : chemicalPerLayer) {
            for (double[] chem : layer) {
                sum += chem[dim];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    public double meanExcitation() { return meanChemical(EnrichedNeuron.EXCITATION); }
    public double meanInhibition() { return meanChemical(EnrichedNeuron.INHIBITION); }
    public double meanNovelty()    { return meanChemical(EnrichedNeuron.NOVELTY); }
    public double meanCertainty()  { return meanChemical(EnrichedNeuron.CERTAINTY); }
}
