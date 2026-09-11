package io.matrix.neuron;

import io.matrix.imports.ChainEnrichedOutput;

/**
 * DESIGN-23 — Free Energy Minimization for chain convergence.
 * Pure function of ChainEnrichedOutput (CONSTITUTION I).
 */
public final class FreeEnergyEvaluator {

    public static final double DEFAULT_ALPHA = 1.0;
    public static final double DEFAULT_BETA = 0.5;
    public static final double DEFAULT_CONVERGENCE_THRESHOLD = -0.2;

    private FreeEnergyEvaluator() {}

    /** F = α·DensityMismatch - β·ConsensusError. */
    public static double freeEnergy(ChainEnrichedOutput output) {
        return freeEnergy(output, DEFAULT_ALPHA, DEFAULT_BETA);
    }

    public static double freeEnergy(ChainEnrichedOutput output,
                                    double alpha, double beta) {
        double densityMismatch = meanDensityMismatch(output);
        double consensusError = 1.0 - output.meanMagnitude();
        return alpha * densityMismatch - beta * consensusError;
    }

    /** True if F < threshold (chain has converged). */
    public static boolean hasConverged(ChainEnrichedOutput output, double threshold) {
        return freeEnergy(output) < threshold;
    }

    public static boolean hasConverged(ChainEnrichedOutput output) {
        return hasConverged(output, DEFAULT_CONVERGENCE_THRESHOLD);
    }

    /** Mean |density - 0.5| across all neurons — lower means more polarized. */
    public static double meanDensityMismatch(ChainEnrichedOutput output) {
        // For each neuron: |magnitude-translated-density| (rough proxy)
        // Magnitude is already a sigmoid of (density-0.5), so |magnitude-0.5|
        // is a reasonable proxy
        double sum = 0;
        long count = 0;
        for (double[] layer : output.magnitudePerLayer()) {
            for (double m : layer) {
                sum += Math.abs(m - 0.5);
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }
}
