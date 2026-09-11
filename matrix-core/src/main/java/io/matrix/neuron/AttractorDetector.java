package io.matrix.neuron;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.ChainEnrichedOutput;
import io.matrix.imports.EnrichedChainEvaluator;

import java.util.Random;

/**
 * DESIGN-28 — Attractor detection (Banach fixed-point / Hopfield).
 * Pure function (CONSTITUTION I).
 */
public final class AttractorDetector {

    public static final double DEFAULT_TOLERANCE = 0.01;
    public static final int DEFAULT_MAX_ITERATIONS = 20;

    private AttractorDetector() {}

    public record AttractorState(
            boolean[] bits,
            int convergenceIteration,
            double basinRadius
    ) {}

    /**
     * Iterate the chain N times with same input. If outputs converge
     * (Hamming distance &lt; tolerance), declare an attractor.
     */
    public static AttractorState detect(
            BooleanChainRunner chain, boolean[] input,
            int maxIterations, double tolerance) {
        if (chain == null || input == null) {
            throw new IllegalArgumentException("null");
        }
        if (maxIterations <= 0) maxIterations = DEFAULT_MAX_ITERATIONS;
        if (tolerance <= 0) tolerance = DEFAULT_TOLERANCE;

        EnrichedChainEvaluator eval = new EnrichedChainEvaluator(chain);
        ChainEnrichedOutput current = eval.evaluateEnriched(input);
        boolean[] previous;
        for (int iter = 1; iter < maxIterations; iter++) {
            previous = current.bits();
            current = eval.evaluateEnriched(input);
            if (hammingDistance(previous, current.bits()) < tolerance) {
                double basin = estimateBasinRadius(eval, current, tolerance);
                return new AttractorState(current.bits(), iter, basin);
            }
        }
        return new AttractorState(current.bits(), maxIterations, 0.0);
    }

    public static AttractorState detect(BooleanChainRunner chain, boolean[] input) {
        return detect(chain, input, DEFAULT_MAX_ITERATIONS, DEFAULT_TOLERANCE);
    }

    private static int hammingDistance(boolean[] a, boolean[] b) {
        int n = Math.min(a.length, b.length);
        int d = 0;
        for (int i = 0; i < n; i++) if (a[i] != b[i]) d++;
        return d;
    }

    /** Basin radius: number of random single-bit perturbations that
     *  still converge to the attractor. */
    private static double estimateBasinRadius(EnrichedChainEvaluator eval,
                                              ChainEnrichedOutput attractor,
                                              double tolerance) {
        boolean[] input = attractor.bits();
        Random rng = new Random(0xBA51FEE5L);  // fixed seed for determinism
        int successCount = 0;
        int totalTrials = 20;
        for (int trial = 0; trial < totalTrials; trial++) {
            boolean[] perturbed = input.clone();
            int idx = rng.nextInt(perturbed.length);
            perturbed[idx] = !perturbed[idx];
            ChainEnrichedOutput out = eval.evaluateEnriched(perturbed);
            if (hammingDistance(out.bits(), attractor.bits()) < tolerance) {
                successCount++;
            }
        }
        return (double) successCount / totalTrials;
    }
}
