package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W304 — Cognitive Early Exit (adaptive computation).
 *
 * <p>Inspired by early-exit networks (Huang et al. 2018) and conditional
 * computation (Bengio et al. 2015). Allow cognitive processing to exit
 * early when confidence is high enough.
 *
 * <p>Strategy:
 * - Run layer(s)
 * - Check confidence
 * - If above threshold → exit
 * - Else → continue
 *
 * <p>CONSTITUTION VI compliance: adaptive cognitive computation,
 * not phenomenal consciousness claim.
 */
public final class CognitiveEarlyExit {

    private CognitiveEarlyExit() {}

    /** Result of early-exit processing. */
    public record EarlyExitResult(
        CognitiveGenesisProfile finalProfile,
        int layersUsed,
        double confidence,
        boolean exited,
        List<Double> confidenceHistory
    ) {}

    /** Functional interface for a "layer" of processing. */
    @FunctionalInterface
    public interface CognitiveLayer {
        CognitiveGenesisProfile apply(CognitiveGenesisProfile input, int layerIndex);
    }

    /** Functional interface for confidence estimation. */
    @FunctionalInterface
    public interface ConfidenceEstimator {
        double estimate(CognitiveGenesisProfile profile);
    }

    /**
     * Process profile with adaptive early-exit.
     */
    public static EarlyExitResult process(CognitiveGenesisProfile initial,
                                            int maxLayers,
                                            double exitThreshold,
                                            CognitiveLayer layer,
                                            ConfidenceEstimator confidenceEstimator) {
        if (initial == null || maxLayers < 1) {
            return new EarlyExitResult(initial, 0, 0.0, true, new ArrayList<>());
        }
        List<Double> confidenceHistory = new ArrayList<>();
        CognitiveGenesisProfile current = initial;
        double confidence = 0.0;
        boolean exited = false;
        int layersUsed = 0;
        for (int i = 0; i < maxLayers; i++) {
            current = (layer != null) ? layer.apply(current, i) : current;
            confidence = (confidenceEstimator != null) ?
                confidenceEstimator.estimate(current) : 0.5;
            confidenceHistory.add(confidence);
            layersUsed++;
            if (confidence >= exitThreshold) {
                exited = true;
                break;
            }
        }
        return new EarlyExitResult(current, layersUsed, confidence, exited, confidenceHistory);
    }

    /**
     * Compute average compute saved across multiple profiles.
     */
    public static double computeSaved(List<EarlyExitResult> results, int maxLayers) {
        if (results == null || results.isEmpty() || maxLayers <= 0) return 0.0;
        int totalUsed = 0;
        for (EarlyExitResult r : results) totalUsed += r.layersUsed();
        double avgUsed = (double) totalUsed / results.size();
        return 1.0 - (avgUsed / maxLayers);
    }
}
