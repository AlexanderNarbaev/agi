package io.matrix.multimodal;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Cross-modal alignment for projecting features between modality spaces.
 * 
 * Uses simple linear projection for feature alignment.
 */
@ApplicationScoped
public class CrossModalAligner {

    private final Map<String, FeatureExtractor> extractors;

    public CrossModalAligner(Map<String, FeatureExtractor> extractors) {
        this.extractors = extractors;
    }

    /**
     * Extract features from input in specified modality.
     */
    public float[] extract(String modality, Object input) {
        FeatureExtractor extractor = extractors.get(modality);
        if (extractor == null) {
            throw new IllegalArgumentException("Unknown modality: " + modality);
        }
        return extractor.extract(input);
    }

    /**
     * Align features from source modality to target modality space.
     * Simple implementation: resize feature vector.
     */
    public float[] align(String sourceModality, String targetModality, float[] features) {
        // Simple alignment: resize to target dimension
        int targetDim = getModalityDimension(targetModality);
        float[] aligned = new float[targetDim];
        
        for (int i = 0; i < targetDim; i++) {
            aligned[i] = features[i % features.length];
        }
        
        return aligned;
    }

    private int getModalityDimension(String modality) {
        return switch (modality) {
            case "text" -> 256;
            case "image" -> 512;
            case "audio" -> 128;
            default -> 256;
        };
    }
}
