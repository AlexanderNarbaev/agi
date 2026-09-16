package io.matrix.consciousness;

import java.util.List;

/**
 * W278 — Cognitive IPO (Identity Preference Optimization).
 *
 * <p>Inspired by IPO (Azar et al. 2023). Regularized DPO that
 * prevents overfitting via identity term.
 *
 * <p>Loss: (log π(y_w)/π(y_l) - log π_ref(y_w)/π_ref(y_l) - 1/(2β))²
 *
 * <p>CONSTITUTION VI compliance: regularized preference cognitive optimization,
 * not phenomenal consciousness claim.
 */
public final class CognitiveIPO {

    private CognitiveIPO() {}

    /** IPO step. */
    public record IPOStep(int step, double loss) {}

    /** IPO result. */
    public record IPOResult(List<IPOStep> history, double finalLoss) {}

    private double beta;

    public CognitiveIPO(long seed) {
        this(0.1);
    }

    public CognitiveIPO(double beta) {
        this.beta = beta;
    }

    /**
     * Compute IPO loss.
     */
    public double loss(double logProbChosen, double logProbRejected,
                        double refLogProbChosen, double refLogProbRejected) {
        double logRatioDiff = (logProbChosen - logProbRejected)
                             - (refLogProbChosen - refLogProbRejected);
        double target = 1.0 / (2.0 * beta);
        double diff = logRatioDiff - target;
        return diff * diff;
    }

    /**
     * Train using IPO.
     */
    public IPOResult train(List<CognitiveRLHF.Preference> preferences, int epochs) {
        if (preferences == null || preferences.isEmpty() || epochs < 1) {
            return new IPOResult(new java.util.ArrayList<>(), 0.0);
        }
        java.util.List<IPOStep> history = new java.util.ArrayList<>();
        double totalLoss = 0;
        for (int step = 0; step < epochs; step++) {
            double sumLoss = 0;
            for (CognitiveRLHF.Preference p : preferences) {
                double logProbChosen = Math.log(p.chosen().phiBinary() + 1e-9);
                double logProbRejected = Math.log(p.rejected().phiBinary() + 1e-9);
                double refChosen = Math.log(0.5);
                double refRejected = Math.log(0.5);
                sumLoss += loss(logProbChosen, logProbRejected, refChosen, refRejected);
            }
            double avgLoss = sumLoss / preferences.size();
            history.add(new IPOStep(step, avgLoss));
            totalLoss += avgLoss;
        }
        return new IPOResult(history, totalLoss / epochs);
    }

    public double beta() { return beta; }
}
