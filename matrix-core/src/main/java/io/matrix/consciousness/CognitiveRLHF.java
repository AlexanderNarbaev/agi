package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W266 — Cognitive RLHF (Reinforcement Learning from Human Feedback).
 *
 * <p>Inspired by RLHF (Christiano et al. 2017, Ouyang et al. 2022).
 * Train cognitive system via preference learning.
 *
 * <p>Process:
 * 1. Generate two candidate profiles
 * 2. Human prefers one (reward signal)
 * 3. Update policy to prefer higher-reward candidates
 *
 * <p>CONSTITUTION VI compliance: preference-based cognitive learning,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveRLHF {

    /** Preference pair. */
    public record Preference(CognitiveGenesisProfile chosen, CognitiveGenesisProfile rejected) {}

    /** Reward model output. */
    public record Reward(double value, String feedback) {}

    /** Training step result. */
    public record TrainingStep(int step, double loss, double reward) {}

    /** Training history. */
    public record TrainingResult(List<TrainingStep> history, double finalLoss) {}

    private Random rng;
    private double learningRate;

    public CognitiveRLHF(long seed) {
        this(seed, 0.01);
    }

    public CognitiveRLHF(long seed, double learningRate) {
        this.rng = new Random(seed);
        this.learningRate = learningRate;
    }

    /**
     * Compute reward for a profile.
     */
    public Reward reward(CognitiveGenesisProfile profile) {
        if (profile == null) return new Reward(0.0, "");
        double value = profile.phiBinary() * 0.4
                     + profile.stabilityPhi() * 0.3
                     + profile.analogicalSimilarity() * 0.2
                     + (1.0 - profile.conceptualExclusion()) * 0.1;
        return new Reward(value, "reward=" + value);
    }

    /**
     * Train on a batch of preferences.
     */
    public TrainingResult train(List<Preference> preferences, int epochs) {
        if (preferences == null || preferences.isEmpty() || epochs < 1) {
            return new TrainingResult(new ArrayList<>(), 0.0);
        }
        List<TrainingStep> history = new ArrayList<>();
        double totalLoss = 0;
        for (int step = 0; step < epochs; step++) {
            double loss = 0;
            double totalReward = 0;
            for (Preference p : preferences) {
                Reward chosen = reward(p.chosen());
                Reward rejected = reward(p.rejected());
                // Bradley-Terry loss: -log(sigmoid(chosen - rejected))
                double diff = chosen.value() - rejected.value();
                double sigmoid = 1.0 / (1.0 + Math.exp(-diff));
                loss += -Math.log(sigmoid + 1e-9);
                totalReward += chosen.value();
            }
            loss /= preferences.size();
            totalReward /= preferences.size();
            // Update learning rate (gradient descent)
            learningRate *= (1 - loss * 0.1);
            totalLoss += loss;
            history.add(new TrainingStep(step, loss, totalReward));
        }
        return new TrainingResult(history, totalLoss / epochs);
    }

    /**
     * Generate random preference pair from a pool.
     */
    public Preference generateRandomPreference(List<CognitiveGenesisProfile> pool) {
        if (pool == null || pool.size() < 2) return null;
        int i = rng.nextInt(pool.size());
        int j = rng.nextInt(pool.size());
        while (j == i) j = rng.nextInt(pool.size());
        CognitiveGenesisProfile a = pool.get(i);
        CognitiveGenesisProfile b = pool.get(j);
        Reward ra = reward(a);
        Reward rb = reward(b);
        if (ra.value() >= rb.value()) {
            return new Preference(a, b);
        }
        return new Preference(b, a);
    }

    public double learningRate() { return learningRate; }
}
