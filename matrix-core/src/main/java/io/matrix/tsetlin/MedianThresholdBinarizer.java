package io.matrix.tsetlin;

import java.util.Arrays;

/**
 * EXP-002 frozen binarization (median-threshold): each feature's threshold
 * is the MEDIAN of that feature over the fitting sample; constant features
 * threshold to bit 0. Fit ONCE, then frozen — every later transform reuses
 * the captured thresholds (card protocol: «вычисляется один раз и
 * замораживается»).
 *
 * <p>Deterministic (sorting only), thread-safe after fit.
 */
public final class MedianThresholdBinarizer {

    private final double[] thresholds;
    private boolean fitted;

    /** @param nFeatures number of numeric features per vector */
    public MedianThresholdBinarizer(int nFeatures) {
        if (nFeatures < 1) throw new IllegalArgumentException("nFeatures >= 1");
        this.thresholds = new double[nFeatures];
    }

    /**
     * Fits thresholds = per-feature median over {@code samples}.
     * Re-fitting is allowed but restarts freezing.
     */
    public void fit(double[][] samples) {
        if (samples.length == 0) throw new IllegalArgumentException("empty sample set");
        int nf = thresholds.length;
        double[] col = new double[samples.length];
        for (int j = 0; j < nf; j++) {
            for (int i = 0; i < samples.length; i++) {
                if (samples[i].length < nf) throw new IllegalArgumentException("short sample at " + i);
                col[i] = samples[i][j];
            }
            Arrays.sort(col);
            int mid = col.length / 2;
            thresholds[j] = (col.length % 2 == 1) ? col[mid] : (col[mid - 1] + col[mid]) / 2.0;
        }
        fitted = true;
    }

    /** @return true between fit() and any subsequent re-fit */
    public boolean isFrozen() { return fitted; }

    private void ensureFrozen() {
        if (!fitted) throw new IllegalStateException("binarizer not fitted");
    }

    /** Binarizes one vector: bit_j = value_j > median_j (strictly greater). */
    public long[] transform(double[] vector) {
        ensureFrozen();
        long[] words = new long[(thresholds.length + 63) >>> 6];
        for (int j = 0; j < thresholds.length; j++) {
            if (vector[j] > thresholds[j]) words[j >>> 6] |= (1L << j);
        }
        return words;
    }

    /** Batch convenience. */
    public long[][] transformAll(double[][] vectors) {
        long[][] out = new long[vectors.length][];
        for (int i = 0; i < vectors.length; i++) out[i] = transform(vectors[i]);
        return out;
    }

    /** Threshold access for audit/freeze verification. */
    public double threshold(int feature) { ensureFrozen(); return thresholds[feature]; }

    public int featureCount() { return thresholds.length; }
}
