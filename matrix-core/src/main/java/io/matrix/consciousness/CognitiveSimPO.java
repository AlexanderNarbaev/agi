package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W274 — Cognitive SimPO (Simple Preference Optimization).
 *
 * <p>Inspired by SimPO (Meng et al. 2024). Simpler than DPO: no
 * reference model needed.
 *
 * <p>Loss: -log σ(β/|y| * (log π(y_w) - log π(y_l)) - γ)
 *
 * <p>Where γ is a margin hyperparameter.
 *
 * <p>CONSTITUTION VI compliance: simple preference cognitive optimization,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveSimPO {

    private CognitiveSimPO() {}

    /** SimPO step. */
    public record SimPOStep(int step, double loss, double margin) {}

    /** SimPO training result. */
    public record SimPOResult(List<SimPOStep> history, double finalLoss) {}

    private double beta;
    private double gamma;
    private Random rng;

    public CognitiveSimPO(long seed) {
        this(seed, 2.0, 1.0);
    }

    public CognitiveSimPO(long seed, double beta, double gamma) {
        this.beta = beta;
        this.gamma = gamma;
        this.rng = new Random(seed);
    }

    /**
     * Compute SimPO loss.
     */
    public double loss(double logProbChosen, double logProbRejected, double seqLen) {
        if (seqLen < 1) seqLen = 1;
        double diff = beta / seqLen * (logProbChosen - logProbRejected) - gamma;
        return -Math.log(1.0 / (1.0 + Math.exp(-diff)) + 1e-9);
    }

    /**
     * Train using SimPO.
     */
    public SimPOResult train(List<CognitiveRLHF.Preference> preferences, int epochs) {
        if (preferences == null || preferences.isEmpty() || epochs < 1) {
            return new SimPOResult(new java.util.ArrayList<>(), 0.0);
        }
        java.util.List<SimPOStep> history = new java.util.ArrayList<>();
        double totalLoss = 0;
        for (int step = 0; step < epochs; step++) {
            double loss = 0;
            for (CognitiveRLHF.Preference p : preferences) {
                double logProbChosen = Math.log(p.chosen().phiBinary() + 1e-9);
                double logProbRejected = Math.log(p.rejected().phiBinary() + 1e-9);
                loss += loss(logProbChosen, logProbRejected, 1.0);
            }
            loss /= preferences.size();
            history.add(new SimPOStep(step, loss, gamma));
            totalLoss += loss;
        }
        return new SimPOResult(history, totalLoss / epochs);
    }

    public double beta() { return beta; }
    public double gamma() { return gamma; }
}
