package io.matrix.consciousness;

import java.util.List;

/**
 * W196 — Φ complexity fingerprint.
 *
 * <p>Single-value summary of profile complexity across multiple
 * measurements:
 * - Information entropy of profile fields
 * - Variability across cycle sequence
 * - Diversity of regime classifications
 * - Combined complexity score
 *
 * <p>Used as a "fingerprint" identifier for cognitive states:
 * similar fingerprints → similar cognitive regimes.
 *
 * <p>CONSTITUTION VI compliance: complexity fingerprint of cognitive
 * state, not phenomenal consciousness claim.
 */
public final class PhiComplexityFingerprint {

    private PhiComplexityFingerprint() {}

    /**
     * Compute fingerprint for a single profile.
     * Returns 4 doubles: [field_entropy, field_variance, regime_complexity, composite].
     */
    public static double[] fingerprint(CognitiveGenesisProfile profile) {
        if (profile == null) return new double[]{0, 0, 0, 0};
        double[] fields = new double[]{
            profile.phiBinary(), profile.phiF(), profile.phiR(), profile.phiLinGauss(),
            profile.interAgentPhi(), profile.stabilityPhi(), profile.crossLevelPhi(),
            Math.min(1.0, profile.kolmogorovK() / 100.0),
            profile.analogicalSimilarity(), profile.conceptualExclusion(),
            profile.nkEdgeOfChaosK() / 8.0, profile.memristorConductance(),
            Math.min(1.0, profile.lSystemComplexityRatio() / 5.0)
        };
        return fingerprintFromFields(fields);
    }

    /**
     * Compute fingerprint for a sequence of profiles.
     */
    public static double[] sequenceFingerprint(List<CognitiveGenesisProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return new double[]{0, 0, 0, 0};
        double[] fieldEntropy = new double[13];
        double[] fieldVariance = new double[13];
        double[] fieldMeans = new double[13];
        double[] fieldCounts = new double[13];

        // First pass: sum and sum of squares
        for (CognitiveGenesisProfile p : profiles) {
            double[] fields = extractFields(p);
            for (int j = 0; j < 13; j++) {
                fieldMeans[j] += fields[j];
                fieldCounts[j]++;
            }
        }
        for (int j = 0; j < 13; j++) {
            fieldMeans[j] /= fieldCounts[j];
        }
        // Second pass: variance
        for (CognitiveGenesisProfile p : profiles) {
            double[] fields = extractFields(p);
            for (int j = 0; j < 13; j++) {
                double d = fields[j] - fieldMeans[j];
                fieldVariance[j] += d * d;
            }
        }
        for (int j = 0; j < 13; j++) {
            fieldVariance[j] /= fieldCounts[j];
        }
        // Entropy: bin into 4 bins and compute entropy
        int[][] bins = new int[13][4];
        for (CognitiveGenesisProfile p : profiles) {
            double[] fields = extractFields(p);
            for (int j = 0; j < 13; j++) {
                int bin = (int) (fields[j] * 4);
                if (bin >= 4) bin = 3;
                if (bin < 0) bin = 0;
                bins[j][bin]++;
            }
        }
        for (int j = 0; j < 13; j++) {
            fieldEntropy[j] = EntropyDecomposition.entropy(toDoubleArray(bins[j]));
        }

        // Composite: average entropy + average variance + regime complexity
        double meanEntropy = mean(fieldEntropy);
        double meanVariance = mean(fieldVariance);
        double regimeComplexity = CognitiveEntropyMeter.normalizedRegimeEntropy(profiles);
        double composite = (meanEntropy + meanVariance + regimeComplexity) / 3.0;
        return new double[]{meanEntropy, meanVariance, regimeComplexity, composite};
    }

    /**
     * Distance between two fingerprints.
     */
    public static double fingerprintDistance(double[] fp1, double[] fp2) {
        if (fp1 == null || fp2 == null) return 0.0;
        int n = Math.min(fp1.length, fp2.length);
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double d = fp1[i] - fp2[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    private static double[] fingerprintFromFields(double[] fields) {
        // Single profile: entropy = max (one sample = max uncertainty in each bin)
        // Use variance of fields as variance measure
        double mean = 0;
        for (double f : fields) mean += f;
        mean /= fields.length;
        double variance = 0;
        for (double f : fields) {
            double d = f - mean;
            variance += d * d;
        }
        variance /= fields.length;
        // Entropy of single profile is just normalized variance in [0, 1]
        double entropy = Math.min(1.0, Math.sqrt(variance) * 4);
        // Regime complexity: 1 - mean(field) as proxy
        double regimeComplexity = 1.0 - mean;
        double composite = (entropy + variance + regimeComplexity) / 3.0;
        return new double[]{entropy, variance, regimeComplexity, composite};
    }

    private static double[] extractFields(CognitiveGenesisProfile p) {
        return new double[]{
            p.phiBinary(), p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            Math.min(1.0, p.kolmogorovK() / 100.0),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK() / 8.0, p.memristorConductance(),
            Math.min(1.0, p.lSystemComplexityRatio() / 5.0)
        };
    }

    private static double[] toDoubleArray(int[] arr) {
        double[] result = new double[arr.length];
        for (int i = 0; i < arr.length; i++) result[i] = arr[i];
        return result;
    }

    private static double mean(double[] arr) {
        double sum = 0;
        for (double v : arr) sum += v;
        return sum / arr.length;
    }
}
