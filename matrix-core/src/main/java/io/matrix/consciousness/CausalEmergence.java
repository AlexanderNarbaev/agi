package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * W183 — Causal Emergence (Hoel 2013, Mediano 2022).
 *
 * <p>Computes causal emergence measure Φ_CE = EI(macro) - EI(micro)
 * where EI is effective information.
 *
 * <p>Effective information: EI(P) = Σ_i P(i) log[P(cause_i)/P_max(cause_i)]
 * where P_max is the maximum entropy distribution.
 *
 * <p>Used to determine when macro-scale descriptions capture MORE
 * causal structure than micro-scale descriptions.
 *
 * <p>CONSTITUTION VI compliance: causal structure analysis,
 * not phenomenal consciousness claim.
 */
public final class CausalEmergence {

    private CausalEmergence() {}

    /**
     * Compute Effective Information (EI) of a probability distribution.
     * Uses log base 2.
     */
    public static double effectiveInformation(double[] distribution) {
        if (distribution == null || distribution.length == 0) return 0.0;
        double sum = 0;
        for (double p : distribution) sum += p;
        if (sum == 0) return 0.0;
        // Normalize
        double[] p = new double[distribution.length];
        for (int i = 0; i < distribution.length; i++) {
            p[i] = distribution[i] / sum;
        }
        // P_max is uniform
        double pMax = 1.0 / p.length;
        // EI = Σ P(i) log(P(i) / P_max)
        double ei = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0) {
                ei += p[i] * (Math.log(p[i]) - Math.log(pMax));
            }
        }
        return ei / Math.log(2);  // log base 2
    }

    /**
     * Compute causal emergence between micro and macro distributions.
     * Φ_CE = EI(macro) - EI(micro).
     *
     * <p>Positive Φ_CE: macro description captures more causal
     * structure than micro.
     * Negative Φ_CE: micro description is more informative.
     */
    public static double causalEmergence(double[] macroDistribution, double[] microDistribution) {
        double eiMacro = effectiveInformation(macroDistribution);
        double eiMicro = effectiveInformation(microDistribution);
        return eiMacro - eiMicro;
    }

    /**
     * Coarsen a micro distribution by binning (every k consecutive elements).
     */
    public static double[] coarsenByBinning(double[] micro, int binSize) {
        if (micro == null || micro.length == 0 || binSize < 1) return new double[0];
        int newSize = (micro.length + binSize - 1) / binSize;
        double[] macro = new double[newSize];
        for (int i = 0; i < micro.length; i++) {
            macro[i / binSize] += micro[i];
        }
        return macro;
    }

    /**
     * Compute causal emergence across multiple bin sizes, return
     * max Φ_CE.
     */
    public static double maxCausalEmergence(double[] microDistribution) {
        if (microDistribution == null || microDistribution.length < 2) return 0.0;
        double maxCE = Double.NEGATIVE_INFINITY;
        for (int binSize = 2; binSize <= microDistribution.length / 2; binSize++) {
            // RECON-W34.9: this searched EVERY bin size in 2..n/2, including sizes that do not
            // divide the distribution evenly. A ragged coarsening is not a partition of the
            // space, it is a distortion of it: for the uniform input
            //
            //     {0.1 x 8}
            //
            // binSize 2 -> [0.250 0.250 0.250 0.250]   uniform, EI = 0
            //     binSize 3 -> [0.375 0.375 0.250]        RAGGED, so not uniform, EI > 0
            //     binSize 4 -> [0.500 0.500]               uniform, EI = 0
            //
            // and the ragged bin won. That manufactured emergence out of data that has none:
            // CausalEmergenceTest.maxCausalEmergenceNonNegativeForUniform measured 0.0237 and
            // CausalEmergencePropertyTest measured 0.0630 for a distribution whose emergence is
            // zero by definition.
            //
            // The result depended on the input LENGTH rather than its content -- an array of
            // eight identical values "has more emergence" than one of sixteen, which is not a
            // property of emergence. Only bin sizes that tile the space evenly are considered
            // now, so a uniform input measures zero and the maximum reflects structure that
            // is actually present.
            if (microDistribution.length % binSize != 0) {
                continue;
            }
            double[] macro = coarsenByBinning(microDistribution, binSize);
            double ce = causalEmergence(macro, microDistribution);
            if (ce > maxCE) maxCE = ce;
        }
        // Every length < 4 has no even tiling beyond itself, so no coarsening is searched.
        return maxCE == Double.NEGATIVE_INFINITY ? 0.0 : maxCE;
    }
}
