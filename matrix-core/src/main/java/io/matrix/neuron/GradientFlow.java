package io.matrix.neuron;

/**
 * DESIGN-38 — Natural gradient on probability manifold.
 * Pure function (CONSTITUTION I).
 */
public final class GradientFlow {

    private GradientFlow() {}

    /**
     * Natural gradient step: Δθ = -F^{-1} ∇L
     * where F is the Fisher information matrix (approximated by
     * its diagonal for tractability).
     */
    public static double[] naturalGradient(double[] params, double[] lossGrad,
                                          double[] fisherDiagonal) {
        if (params == null || lossGrad == null || fisherDiagonal == null) {
            throw new IllegalArgumentException("null");
        }
        if (lossGrad.length != fisherDiagonal.length) {
            throw new IllegalArgumentException("grad/fisher length mismatch");
        }
        double[] result = new double[lossGrad.length];
        for (int i = 0; i < lossGrad.length; i++) {
            double f = fisherDiagonal[i];
            // Avoid div-by-zero: clamp Fisher to small positive
            if (f < 1e-10) f = 1e-10;
            result[i] = -lossGrad[i] / f;
        }
        return result;
    }

    /**
     * Riemannian gradient on probability manifold:
     * grad_R = grad_E / p  (natural gradient for multinoulli).
     */
    public static double[] riemannianGradient(double[] probabilities,
                                              double[] euclideanGrad) {
        if (probabilities == null || euclideanGrad == null) {
            throw new IllegalArgumentException("null");
        }
        if (probabilities.length != euclideanGrad.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        double[] result = new double[euclideanGrad.length];
        for (int i = 0; i < euclideanGrad.length; i++) {
            double p = Math.max(probabilities[i], 1e-10);
            result[i] = euclideanGrad[i] / p;
        }
        return result;
    }
}
