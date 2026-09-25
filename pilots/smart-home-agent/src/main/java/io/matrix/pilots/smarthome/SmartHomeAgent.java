package io.matrix.pilots.smarthome;

import java.util.Objects;

/**
 * WAVE T-08 — Smart Home Agent (Pilot Package).
 *
 * Pre-built solution combining:
 * - Energy optimization (analyze home patterns, suggest savings)
 * - Anomaly detection (identify unusual device behavior)
 *
 * Uses MATRIX's hybrid inference to:
 * 1. Accept natural-language queries about the home
 * 2. Reason over sensor data via BIR rules
 * 3. Retrieve similar past patterns via HDC memory
 * 4. Return explainable decisions
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM is invoked. All reasoning
 * happens server-side in MATRIX.</p>
 */
public final class SmartHomeAgent {

    public enum AnomalyLevel {
        NORMAL, MINOR, MAJOR, CRITICAL
    }

    public record HomeState(
        double indoorTempC,
        double outdoorTempC,
        double energyKwh,
        int occupants,
        boolean hvacRunning,
        boolean lightsOn
    ) {}

    public record Recommendation(
        String title,
        String detail,
        double estimatedSavingsKwh,
        AnomalyLevel severity
    ) {}

    /**
     * Analyze the current home state and return recommendations.
     * Calls MATRIX with a structured natural-language prompt.
     */
    public Recommendation analyze(HomeState state) {
        Objects.requireNonNull(state, "state");
        String prompt = String.format(
            "Given home state: indoor=%.1fC outdoor=%.1fC energy=%.1fkWh "
            + "occupants=%d hvac=%s lights=%s. Recommend energy optimizations.",
            state.indoorTempC(), state.outdoorTempC(), state.energyKwh(),
            state.occupants(), state.hvacRunning(), state.lightsOn()
        );
        // Stub: real impl calls matrixClient.analyze().text(prompt).call()
        return new Recommendation(
            "Adjust HVAC by 1°C",
            "Reduce heating/cooling setpoint by 1°C to save ~5% energy.",
            0.5,
            AnomalyLevel.NORMAL
        );
    }

    /** Detect anomalies in energy consumption vs. historical pattern. */
    public AnomalyLevel detectAnomaly(double currentKwh, double[] history) {
        if (history == null || history.length == 0) return AnomalyLevel.NORMAL;
        double mean = 0;
        for (double h : history) mean += h;
        mean /= history.length;

        double variance = 0;
        for (double h : history) variance += (h - mean) * (h - mean);
        variance /= history.length;
        double stddev = Math.sqrt(variance);
        if (stddev == 0) return AnomalyLevel.NORMAL;

        double z = Math.abs(currentKwh - mean) / stddev;
        if (z > 3.0) return AnomalyLevel.CRITICAL;
        if (z > 2.0) return AnomalyLevel.MAJOR;
        if (z > 1.5) return AnomalyLevel.MINOR;
        return AnomalyLevel.NORMAL;
    }
}
