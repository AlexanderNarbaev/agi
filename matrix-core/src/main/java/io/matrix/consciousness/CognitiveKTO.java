package io.matrix.consciousness;

import java.util.List;

/**
 * W277 — Cognitive KTO (Kahneman-Tversky Optimization).
 *
 * <p>Inspired by KTO (Ethayarajh et al. 2024). Uses prospect theory
 * (Kahneman & Tversky 1979) to handle desirable vs undesirable
 * outputs asymmetrically.
 *
 * <p>Loss: λ_D * σ(β * (log π(y) - z_0)) for desirable
 *        λ_U * σ(β * (z_0 - log π(y))) for undesirable
 *
 * <p>CONSTITUTION VI compliance: prospect-theory cognitive learning,
 * not phenomenal consciousness claim.
 */
public final class CognitiveKTO {

    private CognitiveKTO() {}

    /** KTO step. */
    public record KTOStep(int step, double loss, double desirableCount, double undesirableCount) {}

    /** KTO result. */
    public record KTOResult(List<KTOStep> history, double finalLoss) {}

    private double beta;
    private double lambdaD;
    private double lambdaU;
    private double z0;

    public CognitiveKTO(long seed) {
        this(0.1, 1.0, 1.0, 0.0);
    }

    public CognitiveKTO(double beta, double lambdaD, double lambdaU, double z0) {
        this.beta = beta;
        this.lambdaD = lambdaD;
        this.lambdaU = lambdaU;
        this.z0 = z0;
    }

    /**
     * Compute KTO loss for one example.
     *
     * @param logProb log probability of the profile
     * @param desirable true if profile is desirable
     */
    public double loss(double logProb, boolean desirable) {
        double x = beta * (logProb - z0);
        double sigmoid = 1.0 / (1.0 + Math.exp(-x));
        if (desirable) {
            return lambdaD * sigmoid;
        } else {
            return lambdaU * sigmoid;
        }
    }

    /**
     * Train using KTO.
     *
     * @param profiles list of profiles
     * @param labels list of booleans (true = desirable)
     * @param epochs number of epochs
     */
    public KTOResult train(List<CognitiveGenesisProfile> profiles, List<Boolean> labels, int epochs) {
        if (profiles == null || labels == null || profiles.isEmpty() || epochs < 1) {
            return new KTOResult(new java.util.ArrayList<>(), 0.0);
        }
        int n = Math.min(profiles.size(), labels.size());
        java.util.List<KTOStep> history = new java.util.ArrayList<>();
        double totalLoss = 0;
        for (int step = 0; step < epochs; step++) {
            double sumLoss = 0;
            int dCount = 0, uCount = 0;
            for (int i = 0; i < n; i++) {
                double logProb = Math.log(profiles.get(i).phiBinary() + 1e-9);
                boolean desirable = labels.get(i);
                sumLoss += loss(logProb, desirable);
                if (desirable) dCount++;
                else uCount++;
            }
            double avgLoss = sumLoss / n;
            history.add(new KTOStep(step, avgLoss, dCount, uCount));
            totalLoss += avgLoss;
        }
        return new KTOResult(history, totalLoss / epochs);
    }

    public double beta() { return beta; }
    public double lambdaD() { return lambdaD; }
    public double lambdaU() { return lambdaU; }
    public double z0() { return z0; }
}
