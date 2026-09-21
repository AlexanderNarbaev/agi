package io.matrix.multimodal;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extracts features from image data using histogram-based approach.
 */
@ApplicationScoped
public class ImageFeatureExtractor implements FeatureExtractor {

    @Override
    public float[] extract(Object input) {
        byte[] imageData = (byte[]) input;
        float[] features = new float[512];

        // Simple color histogram (R, G, B channels)
        for (int i = 0; i < imageData.length - 2; i += 3) {
            int r = imageData[i] & 0xFF;
            int g = imageData[i + 1] & 0xFF;
            int b = imageData[i + 2] & 0xFF;

            features[r / 4] += 1.0f;         // R histogram (64 bins)
            features[64 + g / 4] += 1.0f;    // G histogram
            features[128 + b / 4] += 1.0f;   // B histogram
        }

        // Texture features (gradient magnitudes)
        for (int i = 0; i < imageData.length - 1; i++) {
            int diff = Math.abs((imageData[i] & 0xFF) - (imageData[i + 1] & 0xFF));
            features[192 + diff / 4] += 1.0f;
        }

        // Normalize
        float max = 0;
        for (float f : features) max = Math.max(max, f);
        if (max > 0) {
            for (int i = 0; i < features.length; i++) features[i] /= max;
        }

        return features;
    }

    @Override
    public String modality() {
        return "image";
    }
}
