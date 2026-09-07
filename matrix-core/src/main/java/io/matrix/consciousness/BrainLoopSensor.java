package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 250 — BrainLoopSensor (input measurements).
 *
 * <p>Records per-input measurements: input length, derived
 * 256-bit encoding statistics. Useful for sensor fusion and
 * anomaly detection.
 */
public final class BrainLoopSensor {

    public record Measurement(int inputLength,
                                int bitCount,
                                double density,
                                double entropy) {}

    private final List<Measurement> measurements = new ArrayList<>();

    public synchronized void record(Measurement m) {
        measurements.add(m);
    }

    public synchronized List<Measurement> measurements() {
        return new ArrayList<>(measurements);
    }

    public synchronized int size() { return measurements.size(); }

    public synchronized double avgLength() {
        if (measurements.isEmpty()) return 0;
        long total = 0;
        for (Measurement m : measurements) total += m.inputLength();
        return (double) total / measurements.size();
    }

    public synchronized double avgDensity() {
        if (measurements.isEmpty()) return 0;
        double total = 0;
        for (Measurement m : measurements) total += m.density();
        return total / measurements.size();
    }

    /** Compute entropy from 0/1 density: H = -p*log(p) - (1-p)*log(1-p). */
    public static double entropy(double density) {
        if (density <= 0 || density >= 1) return 0;
        return -density * Math.log(density) - (1 - density) * Math.log(1 - density);
    }
}
