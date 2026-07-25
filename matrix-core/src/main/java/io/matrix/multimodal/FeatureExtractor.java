package io.matrix.multimodal;

import java.util.Map;

/**
 * Interface for extracting features from different modalities.
 */
public interface FeatureExtractor {
    /**
     * Extract features from input data.
     * @return feature vector as float array
     */
    float[] extract(Object input);

    /**
     * Get modality name (text, image, audio, video).
     */
    String modality();
}
