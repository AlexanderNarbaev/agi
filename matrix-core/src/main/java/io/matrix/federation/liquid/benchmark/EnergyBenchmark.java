package io.matrix.federation.liquid.benchmark;

import java.util.*;

/**
 * W601 — Energy & Speed Benchmark.
 *
 * Measures ops/sec and estimated Joules/op for MATRIX (CPU) vs LLM (GPU/CPU).
 */
public final class EnergyBenchmark {

    /**
     * Energy measurement.
     */
    public record EnergyMeasurement(
            String modelName,
            long totalOps,
            long durationMs,
            double opsPerSecond,
            double estimatedJoulesPerOp,
            double estimatedCO2Grams,
            String hardware
    ) {}

    /**
     * Summary of energy benchmark.
     */
    public record EnergySummary(
            String modelName,
            EnergyMeasurement measurement,
            double speedupVsBaseline,
            double energyRatioVsBaseline
    ) {}

    /**
     * Run energy benchmark.
     */
    public EnergyMeasurement measure(String modelName, Runnable operation, int iterations, String hardware) {
        // Warmup
        for (int i = 0; i < 10; i++) {
            operation.run();
        }

        // Measure
        long start = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            operation.run();
        }
        long duration = System.currentTimeMillis() - start;

        double opsPerSecond = (double) iterations / (duration / 1000.0);

        // Estimate energy (simplified)
        // CPU TDP ~65W for typical desktop, ~15W for mobile
        double watts = hardware.contains("GPU") ? 250.0 : 65.0;
        double joules = watts * (duration / 1000.0);
        double joulesPerOp = joules / iterations;

        // CO2 estimate (global average: ~0.5 kg CO2/kWh)
        double kwh = joules / 3600000.0;
        double co2Grams = kwh * 500.0;

        return new EnergyMeasurement(
                modelName, iterations, duration, opsPerSecond, joulesPerOp, co2Grams, hardware
        );
    }

    /**
     * Compare two measurements.
     */
    public EnergySummary compare(String modelName, EnergyMeasurement baseline, EnergyMeasurement current) {
        double speedup = current.opsPerSecond() / baseline.opsPerSecond();
        double energyRatio = current.estimatedJoulesPerOp() / baseline.estimatedJoulesPerOp();

        return new EnergySummary(modelName, current, speedup, energyRatio);
    }

    /**
     * Simulated LLM measurement (for comparison).
     */
    public static EnergyMeasurement simulatedLLM(String hardware) {
        // Typical LLM: ~100 tokens/sec on GPU, ~10 tokens/sec on CPU
        double opsPerSecond = hardware.contains("GPU") ? 100.0 : 10.0;
        double watts = hardware.contains("GPU") ? 250.0 : 65.0;
        double joulesPerOp = watts / opsPerSecond;
        double co2Grams = joulesPerOp * 0.000139; // ~0.5 kg/kWh

        return new EnergyMeasurement("LLM-0.5B", 1000, 10000, opsPerSecond, joulesPerOp, co2Grams, hardware);
    }

    /**
     * Generate energy report.
     */
    public static String generateReport(List<EnergySummary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Energy & Speed Benchmark Report\n\n");
        sb.append("| Model | Ops/sec | Joules/op | Speedup | Energy Ratio |\n");
        sb.append("|-------|---------|-----------|---------|-------------|\n");

        for (EnergySummary summary : summaries) {
            sb.append(String.format("| %s | %.1f | %.6f | %.2fx | %.2fx |\n",
                    summary.modelName(),
                    summary.measurement().opsPerSecond(),
                    summary.measurement().estimatedJoulesPerOp(),
                    summary.speedupVsBaseline(),
                    summary.energyRatioVsBaseline()));
        }

        return sb.toString();
    }
}
