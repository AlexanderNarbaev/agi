package io.matrix.consciousness;

/**
 * RUN 256 — BrainLoopProfileAggregator.
 *
 * <p>Combines metrics from multiple sources (latency, health,
 * saturation, time) into a single profile.
 */
public final class BrainLoopProfileAggregator {

    public record AggregatedProfile(
            double arousal,
            double health,
            double saturation,
            double avgLatencyMicros,
            int cycles,
            long timestampMillis) {}

    public static AggregatedProfile aggregate(
            BrainLoopService svc,
            BrainLoopLatency.LatencyStats latency,
            long timestampMillis) {
        var health = BrainLoopHealth.compute(svc);
        var saturation = BrainLoopSaturation.compute(svc, 1000);
        return new AggregatedProfile(
                svc.arousal(),
                health.score(),
                saturation.saturationRatio(),
                latency.avgMicros(),
                saturation.cycles(),
                timestampMillis);
    }

    public static String format(AggregatedProfile p) {
        return String.format(
                "Profile{ arousal=%.2f, health=%.2f, sat=%.2f, latency=%.0fµs, cycles=%d }",
                p.arousal(), p.health(), p.saturation(),
                p.avgLatencyMicros(), p.cycles());
    }
}
