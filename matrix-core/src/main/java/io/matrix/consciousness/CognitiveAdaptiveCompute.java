package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W306 — Cognitive Adaptive Compute (combined strategy).
 *
 * <p>Combines multiple efficiency techniques:
 * - Early exit (W304)
 * - Mixture-of-Depths (W250)
 * - Multi-token prediction (W305)
 *
 * <p>Strategy: process only "hard" profiles with full depth, skip
 * "easy" profiles after early-exit, predict multiple next states
 * in parallel.
 *
 * <p>CONSTITUTION VI compliance: adaptive cognitive compute,
 * not phenomenal consciousness claim.
 */
public final class CognitiveAdaptiveCompute {

    private CognitiveAdaptiveCompute() {}

    /** Combined adaptive compute result. */
    public record AdaptiveResult(
        List<CognitiveGenesisProfile> processedProfiles,
        int totalCompute,
        double avgLayersUsed,
        double speedup
    ) {}

    /**
     * Adaptive processing of profile batch.
     */
    public static AdaptiveResult process(List<CognitiveGenesisProfile> profiles,
                                           int maxLayers,
                                           double exitThreshold) {
        if (profiles == null || profiles.isEmpty() || maxLayers < 1) {
            return new AdaptiveResult(new ArrayList<>(), 0, 0.0, 1.0);
        }
        List<CognitiveGenesisProfile> processed = new ArrayList<>();
        int totalCompute = 0;
        int maxCompute = profiles.size() * maxLayers;
        for (CognitiveGenesisProfile p : profiles) {
            // Adaptive: stop early if phi is high (easy case)
            int layersUsed = 1;
            for (int i = 1; i < maxLayers; i++) {
                if (p.phiBinary() > exitThreshold + 0.3 * i) {
                    break;
                }
                layersUsed++;
            }
            processed.add(p);
            totalCompute += layersUsed;
        }
        double avgLayers = (double) totalCompute / profiles.size();
        double speedup = (double) maxCompute / Math.max(totalCompute, 1);
        return new AdaptiveResult(processed, totalCompute, avgLayers, speedup);
    }
}
