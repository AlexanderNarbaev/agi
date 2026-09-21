package io.matrix.consciousness;

/**
 * RUN 226 — BrainLoopSaturation (overload detector).
 *
 * <p>Measures saturation as ratio of cycles to capacity. If
 * saturation > 1, the brain is overloaded. Helps detect
 * runaway cycles or queue buildup.
 */
public final class BrainLoopSaturation {

    public record SaturationReport(int cycles, int capacity,
                                   double saturationRatio,
                                   boolean overloaded) {}

    public static SaturationReport compute(BrainLoopService svc, int capacity) {
        int cycles = svc.trace().count() / 5;
        double ratio = capacity == 0 ? 0 : (double) cycles / capacity;
        return new SaturationReport(cycles, capacity,
                ratio, ratio > 1.0);
    }

    public static String format(SaturationReport r) {
        return String.format(
                "Saturation{ cycles=%d, capacity=%d, ratio=%.2f, overloaded=%s }",
                r.cycles(), r.capacity(), r.saturationRatio(),
                r.overloaded() ? "YES" : "no");
    }
}
