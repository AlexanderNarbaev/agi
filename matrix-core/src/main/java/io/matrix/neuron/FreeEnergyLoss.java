package io.matrix.neuron;

/**
 * RUN 470 — FreeEnergyLoss (DESIGN-60).
 *
 * <p>Unified loss function for MATRIX brain training based on the Free
 * Energy Principle (Friston 2010). Combines:
 * <ul>
 *   <li>Wake loss: prediction error between observation and prediction
 *       (reconstruction)</li>
 *   <li>Sleep loss: prediction error between fantasy and generated output
 *       (generative model accuracy)</li>
 *   <li>Complexity term: KL divergence between approximate and true posterior
 *       (regularization)</li>
 * </ul>
 *
 * <h2>Replaces heuristic FreeEnergyEvaluator</h2>
 * The existing {@code FreeEnergyEvaluator} from DESIGN-23 uses a heuristic
 * 2-term approximation:
 * <pre>
 *   F = α · DensityMismatch - β · ConsensusError
 * </pre>
 * This new class replaces it with the principled Friston variational
 * free energy formulation adapted for BitLinear/HDC substrates.
 *
 * <h2>CONSTITUTION I</h2>
 * Pure function. No RNG, no wall-clock. Deterministic given inputs.
 */
public final class FreeEnergyLoss {

    private FreeEnergyLoss() {}

    /**
     * Compute free energy components.
     *
     * @param observation     actual observation vector
     * @param prediction      predicted observation from generative model
     * @param fantasyState    top-down fantasy generated during sleep
     * @param generatedOutput bottom-up output generated from fantasy
     * @return FreeEnergyResult with wake/sleep/complexity components
     */
    public static FreeEnergyResult compute(
            float[] observation,
            float[] prediction,
            float[] fantasyState,
            float[] generatedOutput) {
        if (observation == null || prediction == null) {
            throw new IllegalArgumentException("observation/prediction null");
        }
        if (observation.length != prediction.length) {
            throw new IllegalArgumentException("observation/prediction length mismatch");
        }

        double wakeLoss = predictiveError(observation, prediction);

        double sleepLoss = 0.0;
        if (fantasyState != null && generatedOutput != null) {
            // Compare fantasy (top-down) with generated output (bottom-up)
            int n = Math.min(fantasyState.length, generatedOutput.length);
            sleepLoss = predictiveError(
                    java.util.Arrays.copyOf(generatedOutput, n),
                    java.util.Arrays.copyOf(fantasyState, n));
        }

        // Complexity term: variance of prediction residuals (proxy for KL)
        double complexity = variance(observation, prediction);

        // Total free energy (lower is better)
        double total = wakeLoss + sleepLoss + 0.1 * complexity;

        return new FreeEnergyResult(total, wakeLoss, sleepLoss, complexity);
    }

    /**
     * Compute prediction error (Euclidean distance, normalized).
     */
    private static double predictiveError(float[] a, float[] b) {
        double sumSq = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / a.length);
    }

    /**
     * Variance of residuals (proxy for KL divergence).
     */
    private static double variance(float[] a, float[] b) {
        double mean = 0.0;
        for (int i = 0; i < a.length; i++) mean += (a[i] - b[i]);
        mean /= a.length;
        double var = 0.0;
        for (int i = 0; i < a.length; i++) {
            double diff = (a[i] - b[i]) - mean;
            var += diff * diff;
        }
        return var / a.length;
    }

    /**
     * Result containing free energy components.
     */
    public record FreeEnergyResult(
            double totalFreeEnergy,
            double wakeLoss,
            double sleepLoss,
            double complexityTerm) {
    }
}
