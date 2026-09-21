package io.matrix.consciousness;

/**
 * RUN 238 — CycleAggregator (sliding window statistics).
 *
 * <p>Aggregates cycle results into per-window statistics.
 * Used for trend analysis and dashboards.
 */
public final class CycleAggregator {

    public record WindowStats(int cycles,
                              int accepted,
                              int denied,
                              double acceptedRatio,
                              double avgArousal) {}

    public static WindowStats aggregate(BrainLoopService svc, int windowSize) {
        int traceCount = svc.trace().count();
        int cycles = traceCount / 5;
        // Approximation: accept ratio is 1.0 since we don't track
        // per-cycle accept/deny in the simple brain loop.
        // For full tracking, integrate with BrainLoopEvent recorder.
        int accepted = cycles;  // baseline assumption
        int denied = 0;
        double ratio = cycles == 0 ? 0 : (double) accepted / cycles;
        return new WindowStats(Math.min(cycles, windowSize),
                Math.min(accepted, windowSize),
                Math.min(denied, windowSize),
                ratio, svc.arousal());
    }

    public static String format(WindowStats w) {
        return String.format(
                "Window{ cycles=%d, accepted=%d, denied=%d, ratio=%.2f, arousal=%.2f }",
                w.cycles(), w.accepted(), w.denied(),
                w.acceptedRatio(), w.avgArousal());
    }
}
