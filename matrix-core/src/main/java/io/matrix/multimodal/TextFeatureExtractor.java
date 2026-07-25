package io.matrix.multimodal;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Extracts features from text input using boolean encoding.
 */
@ApplicationScoped
public class TextFeatureExtractor implements FeatureExtractor {

    @Override
    public float[] extract(Object input) {
        String text = input.toString();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        float[] features = new float[256];
        
        // Byte-frequency features
        for (byte b : bytes) {
            features[b & 0xFF] += 1.0f;
        }
        
        // Normalize
        float max = 0;
        for (float f : features) {
            max = Math.max(max, f);
        }
        if (max > 0) {
            for (int i = 0; i < features.length; i++) {
                features[i] /= max;
            }
        }
        
        return features;
    }

    @Override
    public String modality() {
        return "text";
    }
}
