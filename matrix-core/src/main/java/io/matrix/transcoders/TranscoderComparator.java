package io.matrix.transcoders;

import java.util.*;

/**
 * W1231 — Transcoder Comparator.
 *
 * Runs Pure Symbolic (Path B) vs Distilled ONNX (Path A) in parallel.
 * Compares outputs, logs divergence, gradually shifts weight.
 *
 * Target: >80% parity on simple tasks.
 */
public final class TranscoderComparator {

    public enum Modality { AUDIO, IMAGE }

    public record ComparisonResult(
            Modality modality,
            int totalSamples,
            int matching,
            double parityScore,
            double pathAWeight,
            double pathBWeight,
            List<String> divergences
    ) {}

    private double pathAWeight = 0.8; // Start with 80% ONNX
    private double pathBWeight = 0.2; // 20% Pure Symbolic
    private final List<String> divergenceLog = new ArrayList<>();

    /**
     * Compare audio transcoders.
     */
    public ComparisonResult compareAudio(float[] sample1, float[] sample2, int sampleRate) {
        AudioFFTEncoder symbolic = new AudioFFTEncoder(1024, 8, 42L);
        boolean[] hdcSymbolic = symbolic.encode(sample1, sampleRate);
        boolean[] hdcONNX = simulateONNXAudio(sample2, sampleRate);

        int matching = 0;
        List<String> divergences = new ArrayList<>();
        for (int i = 0; i < Math.min(hdcSymbolic.length, hdcONNX.length); i++) {
            if (hdcSymbolic[i] == hdcONNX[i]) {
                matching++;
            } else if (divergences.size() < 10) {
                divergences.add("Bit " + i + ": symbolic=" + hdcSymbolic[i] + " onnx=" + hdcONNX[i]);
            }
        }

        double parity = (double) matching / hdcSymbolic.length;
        updateWeights(parity);
        divergenceLog.addAll(divergences);

        return new ComparisonResult(Modality.AUDIO, hdcSymbolic.length, matching,
            parity, pathAWeight, pathBWeight, divergences);
    }

    /**
     * Compare image transcoders.
     */
    public ComparisonResult compareImage(byte[] image, int width, int height) {
        VisionEdgeEncoder symbolic = new VisionEdgeEncoder(1024, 50);
        boolean[] hdcSymbolic = symbolic.encode(image, width, height);
        boolean[] hdcONNX = simulateONNXImage(image, width, height);

        int matching = 0;
        List<String> divergences = new ArrayList<>();
        for (int i = 0; i < Math.min(hdcSymbolic.length, hdcONNX.length); i++) {
            if (hdcSymbolic[i] == hdcONNX[i]) {
                matching++;
            }
        }

        double parity = (double) matching / hdcSymbolic.length;
        updateWeights(parity);

        return new ComparisonResult(Modality.IMAGE, hdcSymbolic.length, matching,
            parity, pathAWeight, pathBWeight, divergences);
    }

    /**
     * Gradually shift weight to Path B as parity improves.
     */
    private void updateWeights(double parity) {
        if (parity > 0.8) {
            // Path B is good enough — increase its weight
            pathBWeight = Math.min(0.5, pathBWeight + 0.01);
            pathAWeight = 1.0 - pathBWeight;
        } else if (parity < 0.5) {
            // Path B is too inaccurate — decrease its weight
            pathBWeight = Math.max(0.1, pathBWeight - 0.01);
            pathAWeight = 1.0 - pathBWeight;
        }
    }

    /**
     * Simulated ONNX output (for testing without actual model).
     */
    private boolean[] simulateONNXAudio(float[] samples, int sampleRate) {
        boolean[] result = new boolean[1024];
        // Simple feature-based encoding for comparison
        for (int i = 0; i < result.length; i++) {
            double phase = 2 * Math.PI * i * 440.0 / sampleRate;
            result[i] = Math.sin(phase) > 0;
        }
        return result;
    }

    private boolean[] simulateONNXImage(byte[] image, int width, int height) {
        boolean[] result = new boolean[1024];
        // Simple pixel-based encoding for comparison
        for (int i = 0; i < Math.min(result.length, image.length); i++) {
            result[i] = (image[i] & 0x80) != 0;
        }
        return result;
    }

    public double getPathAWeight() { return pathAWeight; }
    public double getPathBWeight() { return pathBWeight; }
    public List<String> getDivergenceLog() { return new ArrayList<>(divergenceLog); }
}
