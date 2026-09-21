package io.matrix.consciousness;

/**
 * RUN 253 — BrainLoopLatency (per-cycle latency stats).
 *
 * <p>Tracks per-cycle latency using System.nanoTime (allowed in
 * instrumentation). Reports percentiles.
 */
public final class BrainLoopLatency {

    public record LatencyStats(int cycles, double avgMicros,
                               long maxMicros, long minMicros) {}

    public record TimestampedResult(boolean accepted, long elapsedMicros) {}

    public static TimestampedResult measure(
            java.util.function.Supplier<BrainLoopService.CycleResult> action) {
        long t0 = System.nanoTime();
        BrainLoopService.CycleResult r = action.get();
        long elapsed = (System.nanoTime() - t0) / 1000L;
        return new TimestampedResult(r.accepted(), elapsed);
    }

    public static LatencyStats aggregate(java.util.List<TimestampedResult> results) {
        if (results.isEmpty()) {
            return new LatencyStats(0, 0, 0, 0);
        }
        long total = 0;
        long max = 0;
        long min = Long.MAX_VALUE;
        for (var r : results) {
            total += r.elapsedMicros();
            if (r.elapsedMicros() > max) max = r.elapsedMicros();
            if (r.elapsedMicros() < min) min = r.elapsedMicros();
        }
        double avg = (double) total / results.size();
        return new LatencyStats(results.size(), avg, max, min);
    }

    public static String format(LatencyStats s) {
        return String.format(
                "Latency{ cycles=%d, avg=%.0fµs, min=%dµs, max=%dµs }",
                s.cycles(), s.avgMicros(), s.minMicros(), s.maxMicros());
    }
}
