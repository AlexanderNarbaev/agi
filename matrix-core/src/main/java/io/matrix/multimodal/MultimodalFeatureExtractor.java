package io.matrix.multimodal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Unified multi-modal feature extractor.
 * 
 * Extracts and merges features from multiple modalities (text, image, audio)
 * into a unified boolean representation for the Matrix agent.
 */
@ApplicationScoped
public class MultimodalFeatureExtractor {

    private static final Logger log = LoggerFactory.getLogger(MultimodalFeatureExtractor.class);

    @Inject
    UnifiedRepresentation representation;

    @Inject
    CrossModalAligner aligner;

    /**
     * Extract features from multi-modal input.
     */
    public Map<String, float[]> extractFeatures(Map<String, Object> inputs) {
        var result = new java.util.HashMap<String, float[]>();
        for (var entry : inputs.entrySet()) {
            try {
                float[] features = aligner.extract(entry.getKey(), entry.getValue());
                result.put(entry.getKey(), features);
            } catch (Exception e) {
                log.warn("Feature extraction failed for {}: {}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    /**
     * Convert multi-modal input to unified boolean vector.
     */
    public boolean[] toUnifiedVector(Map<String, Object> inputs) {
        boolean[][] vectors = inputs.entrySet().stream()
                .map(e -> representation.toBooleanVector(e.getValue(), e.getKey()))
                .toArray(boolean[][]::new);
        return representation.merge(vectors);
    }

    /**
     * Get supported modalities.
     */
    public List<String> getSupportedModalities() {
        return List.of("text", "image", "audio");
    }
}
