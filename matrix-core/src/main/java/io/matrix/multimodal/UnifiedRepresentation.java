package io.matrix.multimodal;

import io.matrix.neuron.TruthTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Unified representation for multi-modal data.
 * 
 * Converts between different modalities and TruthTable representations.
 */
@ApplicationScoped
public class UnifiedRepresentation {

    @Inject
    CrossModalAligner aligner;

    /**
     * Convert input to boolean feature vector.
     */
    public boolean[] toBooleanVector(Object input, String modality) {
        float[] features = aligner.extract(modality, input);
        boolean[] bits = new boolean[features.length];
        for (int i = 0; i < features.length; i++) {
            bits[i] = features[i] > 0.5f;
        }
        return bits;
    }

    /**
     * Convert boolean vector to float features.
     */
    public float[] toFeatureVector(boolean[] bits) {
        float[] features = new float[bits.length];
        for (int i = 0; i < bits.length; i++) {
            features[i] = bits[i] ? 1.0f : 0.0f;
        }
        return features;
    }

    /**
     * Merge multiple boolean vectors using XOR.
     */
    public boolean[] merge(boolean[][] vectors) {
        if (vectors.length == 0) return new boolean[0];
        
        int maxLen = 0;
        for (boolean[] v : vectors) {
            maxLen = Math.max(maxLen, v.length);
        }
        
        boolean[] merged = new boolean[maxLen];
        for (boolean[] v : vectors) {
            for (int i = 0; i < v.length; i++) {
                merged[i] ^= v[i];
            }
        }
        return merged;
    }
}
