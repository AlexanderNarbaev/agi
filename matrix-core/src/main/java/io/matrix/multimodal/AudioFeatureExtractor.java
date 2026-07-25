package io.matrix.multimodal;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Extracts features from audio data using energy-based approach.
 */
@ApplicationScoped
public class AudioFeatureExtractor implements FeatureExtractor {

    @Override
    public float[] extract(Object input) {
        byte[] audioData = (byte[]) input;
        float[] features = new float[128];

        // Parse as 16-bit PCM samples
        int sampleCount = audioData.length / 2;
        if (sampleCount == 0) return features;

        // Energy in windows
        int windowSize = Math.max(1, sampleCount / 64);
        for (int i = 0; i < sampleCount && i / windowSize < 64; i++) {
            int sample = (audioData[i * 2] & 0xFF) | ((audioData[i * 2 + 1] & 0xFF) << 8);
            if (sample > 32767) sample -= 65536;
            features[i / windowSize] += (float)(sample * sample);
        }

        // Zero-crossing rate
        int crossings = 0;
        for (int i = 1; i < sampleCount; i++) {
            int prev = (audioData[(i-1) * 2] & 0xFF) | ((audioData[(i-1) * 2 + 1] & 0xFF) << 8);
            int curr = (audioData[i * 2] & 0xFF) | ((audioData[i * 2 + 1] & 0xFF) << 8);
            if ((prev > 0 && curr < 0) || (prev < 0 && curr > 0)) crossings++;
        }
        features[64] = (float) crossings / sampleCount;

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
        return "audio";
    }
}
