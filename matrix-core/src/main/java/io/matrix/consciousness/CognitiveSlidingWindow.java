package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W209 — Cognitive Sliding Window (StreamingLLM-style).
 *
 * <p>Inspired by StreamingLLM (Xiao et al. 2023). Maintain a window
 * over a profile sequence with:
 * - Attention sinks: a fixed number of "key" profiles kept permanently
 * - Sliding window: most recent profiles
 *
 * <p>This allows processing long cognitive profile histories without
 * memory explosion.
 *
 * <p>Use cases:
 * - Memory-bounded cognitive history
 * - Identify "key" profiles (attention sinks)
 * - Real-time cognitive monitoring
 *
 * <p>CONSTITUTION VI compliance: efficient memory-bounded cognitive
 * history, not phenomenal consciousness claim.
 */
public final class CognitiveSlidingWindow {

    private final int sinkCount;
    private final int windowSize;
    private final List<CognitiveGenesisProfile> sinks;
    private final List<CognitiveGenesisProfile> window;

    /**
     * Construct sliding window.
     *
     * @param sinkCount number of "key" profiles kept permanently
     * @param windowSize size of sliding window
     */
    public CognitiveSlidingWindow(int sinkCount, int windowSize) {
        if (sinkCount < 0) throw new IllegalArgumentException("sinkCount must be >= 0");
        if (windowSize < 1) throw new IllegalArgumentException("windowSize must be >= 1");
        this.sinkCount = sinkCount;
        this.windowSize = windowSize;
        this.sinks = new ArrayList<>();
        this.window = new ArrayList<>();
    }

    /**
     * Add a profile to the sliding window.
     * Sinks are selected automatically (highest entropy = most important).
     */
    public void add(CognitiveGenesisProfile profile) {
        if (profile == null) return;
        window.add(profile);
        if (window.size() > windowSize) {
            window.remove(0);
        }
        // Maybe promote a profile to sink
        if (sinks.size() < sinkCount) {
            sinks.add(profile);
        } else {
            // Check if new profile has higher entropy than a sink
            CognitiveGenesisProfile newProfile = profile;
            double newEntropy = profile.unifiedComplexityScore();
            // Find min-entropy sink
            int minIdx = -1;
            double minEntropy = Double.MAX_VALUE;
            for (int i = 0; i < sinks.size(); i++) {
                double e = sinks.get(i).unifiedComplexityScore();
                if (e < minEntropy) {
                    minEntropy = e;
                    minIdx = i;
                }
            }
            if (minIdx >= 0 && newEntropy > minEntropy) {
                sinks.set(minIdx, newProfile);
            }
        }
    }

    /**
     * Get all profiles (sinks first, then window).
     */
    public List<CognitiveGenesisProfile> all() {
        List<CognitiveGenesisProfile> result = new ArrayList<>(sinks);
        result.addAll(window);
        return result;
    }

    /**
     * Get just the sinks (most important profiles).
     */
    public List<CognitiveGenesisProfile> sinks() {
        return new ArrayList<>(sinks);
    }

    /**
     * Get just the sliding window (most recent).
     */
    public List<CognitiveGenesisProfile> window() {
        return new ArrayList<>(window);
    }

    /**
     * Total size (sinks + window).
     */
    public int size() {
        return sinks.size() + window.size();
    }

    /**
     * Compute window coverage: ratio of window size to capacity.
     */
    public double windowUtilization() {
        return (double) window.size() / windowSize;
    }

    /**
     * Compute sink coverage: ratio of sinks to sinkCount.
     */
    public double sinkUtilization() {
        if (sinkCount == 0) return 1.0;
        return (double) sinks.size() / sinkCount;
    }

    /** Number of sinks (key profiles). */
    public int sinkCount() { return sinks.size(); }

    /** Window size (current count). */
    public int windowCount() { return window.size(); }

    /** Max window size. */
    public int maxWindowSize() { return windowSize; }
}
