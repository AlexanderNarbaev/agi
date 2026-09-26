package io.matrix.brain.runtime;

/**
 * TRUE-W11 iteration #4 — Free Energy Principle (Friston 2010) estimator.
 *
 * <p>Computes the variational free energy F = E_q[ -ln p(o|s) ] + D_KL(q || p)
 * for a 1-D discrete hidden-state distribution. The first term is the
 * accuracy (or expected surprise) and the second is the complexity
 * (KL divergence between posterior and prior).</p>
 *
 * <p>The mind's <code>predictionError</code> field is essentially this
 * first term scaled; this implementation gives a principled
 * decomposition into accuracy + complexity and exposes both for
 * downstream learning/sleep loops.</p>
 */
public final class FreeEnergyEstimator {

    private final int nStates;
    private final double[] prior;
    private final double[] posterior;
    private final double[] likelihood;   // p(o|s) for current obs
    private int lastObservation = -1;

    public FreeEnergyEstimator(int nStates) {
        if (nStates < 2) throw new IllegalArgumentException("nStates >= 2");
        this.nStates = nStates;
        this.prior = new double[nStates];
        this.posterior = new double[nStates];
        this.likelihood = new double[nStates];
        double uniform = 1.0 / nStates;
        for (int i = 0; i < nStates; i++) prior[i] = uniform;
    }

    /** Update prior by Laplace smoothing from the previous posterior. */
    public void setObservation(int observationIndex) {
        if (observationIndex < 0 || observationIndex >= nStates)
            throw new IllegalArgumentException("observation out of range");
        // Copy posterior to next prior (with smoothing to keep mass)
        for (int i = 0; i < nStates; i++) {
            prior[i] = 0.9 * posterior[i] + 0.1 * (1.0 / nStates);
        }
        // Likelihood = peaked Gaussian at observation
        for (int i = 0; i < nStates; i++) {
            double d = Math.abs(i - observationIndex);
            d = Math.min(d, nStates - d);   // toroidal distance
            likelihood[i] = Math.exp(-d * d);
        }
        // Renormalise likelihood
        double sum = 0;
        for (double l : likelihood) sum += l;
        for (int i = 0; i < nStates; i++) likelihood[i] /= sum;

        // Posterior ∝ prior * likelihood
        double z = 0;
        for (int i = 0; i < nStates; i++) {
            posterior[i] = prior[i] * likelihood[i];
            z += posterior[i];
        }
        if (z > 0) {
            for (int i = 0; i < nStates; i++) posterior[i] /= z;
        }
        lastObservation = observationIndex;
    }

    /** F = accuracy + complexity.  Always >= 0. */
    public double freeEnergy() {
        double accuracy = 0;
        for (int i = 0; i < nStates; i++) {
            if (likelihood[i] > 0) {
                accuracy -= posterior[i] * Math.log(likelihood[i]);
            }
        }
        double complexity = 0;
        for (int i = 0; i < nStates; i++) {
            if (posterior[i] > 0 && prior[i] > 0) {
                complexity += posterior[i] * Math.log(posterior[i] / prior[i]);
            }
        }
        return accuracy + complexity;
    }

    /** Epistemic value = uncertainty of posterior (Shannon entropy). */
    public double epistemicValue() {
        double h = 0;
        for (int i = 0; i < nStates; i++) {
            if (posterior[i] > 0) h -= posterior[i] * Math.log(posterior[i]);
        }
        return h;
    }

    /** Most-likely hidden state given the last observation. */
    public int inferredState() {
        int best = 0;
        double bestP = posterior[0];
        for (int i = 1; i < nStates; i++) {
            if (posterior[i] > bestP) { bestP = posterior[i]; best = i; }
        }
        return best;
    }

    public int lastObservation() { return lastObservation; }
    public int nStates() { return nStates; }
}
