package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W273 — Cognitive DPO (Direct Preference Optimization).
 *
 * <p>Inspired by DPO (Rafailov et al. 2023). Direct preference
 * optimization without RL — simpler than RLHF.
 *
 * <p>Loss: -log σ(β * (log π(y_w) - log π(y_l) - log π_ref(y_w) + log π_ref(y_l)))
 *
 * <p>CONSTITUTION VI compliance: direct preference cognitive optimization,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveDPO {

    private CognitiveDPO() {}

    /** DPO training step. */
    public record DPOStep(int step, double loss, double accuracy) {}

    /** DPO training result. */
    public record DPOResult(List<DPOStep> history, double finalLoss, double finalAccuracy) {}

    private double beta;
    private Random rng;

    public CognitiveDPO(long seed) {
        this(seed, 0.1);
    }

    public CognitiveDPO(long seed, double beta) {
        this.beta = beta;
        this.rng = new Random(seed);
    }

    /**
     * Compute DPO loss for a preference pair.
     */
    public double loss(double logProbChosen, double logProbRejected,
                        double refLogProbChosen, double refLogProbRejected) {
        double logits = beta * ((logProbChosen - logProbRejected)
                                  - (refLogProbChosen - refLogProbRejected));
        return -Math.log(1.0 / (1.0 + Math.exp(-logits)) + 1e-9);
    }

    /**
     * Compute accuracy: how often chosen has higher probability than rejected.
     */
    public double accuracy(double logProbChosen, double logProbRejected) {
        return logProbChosen > logProbRejected ? 1.0 : 0.0;
    }

    /**
     * Train on preferences using DPO.
     */
    public DPOResult train(List<CognitiveRLHF.Preference> preferences, int epochs) {
        if (preferences == null || preferences.isEmpty() || epochs < 1) {
            return new DPOResult(new java.util.ArrayList<>(), 0.0, 0.0);
        }
        java.util.List<DPOStep> history = new java.util.ArrayList<>();
        double totalLoss = 0;
        double totalAcc = 0;
        for (int step = 0; step < epochs; step++) {
            double loss = 0;
            double acc = 0;
            for (CognitiveRLHF.Preference p : preferences) {
                // Simulate log probs from phi
                double logProbChosen = Math.log(p.chosen().phiBinary() + 1e-9);
                double logProbRejected = Math.log(p.rejected().phiBinary() + 1e-9);
                // Reference (use uniform as reference)
                double refLogProbChosen = Math.log(0.5);
                double refLogProbRejected = Math.log(0.5);
                loss += this.loss(logProbChosen, logProbRejected,
                                   refLogProbChosen, refLogProbRejected);
                acc += accuracy(logProbChosen, logProbRejected);
            }
            loss /= preferences.size();
            acc /= preferences.size();
            history.add(new DPOStep(step, loss, acc));
            totalLoss += loss;
            totalAcc += acc;
        }
        return new DPOResult(history, totalLoss / epochs, totalAcc / epochs);
    }

    public double beta() { return beta; }
}
