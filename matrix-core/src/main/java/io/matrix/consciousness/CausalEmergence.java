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
            double[] macro = coarsenByBinning(microDistribution, binSize);
            double ce = causalEmergence(macro, microDistribution);
            if (ce > maxCE) maxCE = ce;
        }
        return maxCE;
    }
}
