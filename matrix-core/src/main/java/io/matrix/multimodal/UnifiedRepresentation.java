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
        float[] features;
        if (aligner != null) {
            features = aligner.extract(modality, input);
        } else {
            features = defaultFeatures(input, modality);
        }
        boolean[] bits = new boolean[features.length];
        for (int i = 0; i < features.length; i++) {
            bits[i] = features[i] > 0.5f;
        }
        return bits;
    }

    private float[] defaultFeatures(Object input, String modality) {
        int dim = switch (modality) {
            case "text" -> 256;
            case "image" -> 512;
            case "audio" -> 128;
            default -> 256;
        };
        float[] features = new float[dim];
        int seed = input == null ? 0 : input.hashCode();
        for (int i = 0; i < dim; i++) {
            features[i] = ((seed + i) & 0xFF) / 255.0f;
        }
        return features;
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
        if (vectors == null || vectors.length == 0) return new boolean[0];
        for (var v : vectors) {
            if (v == null) throw new IllegalArgumentException("null vector in merge input");
        }
        
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
