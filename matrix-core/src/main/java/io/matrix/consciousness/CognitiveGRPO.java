package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W275 — Cognitive GRPO (Group Relative Policy Optimization).
 *
 * <p>Inspired by GRPO (Shao et al. 2024, DeepSeekMath). Group-relative
 * advantage estimation for efficient RL.
 *
 * <p>Process:
 * - Group of K profiles
 * - Compute reward for each
 * - Normalize within group (mean=0, std=1)
 * - Advantage = normalized reward
 * - Policy update based on advantages
 *
 * <p>CONSTITUTION VI compliance: group-relative cognitive optimization,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveGRPO {

    private CognitiveGRPO() {}

    /** GRPO group result. */
    public record GRPOGroup(
        List<Double> rewards,
        List<Double> advantages,
        double meanReward,
        double stdReward
    ) {}

    private double clipRatio;
    private Random rng;

    public CognitiveGRPO(long seed) {
        this(seed, 0.2);
    }

    public CognitiveGRPO(long seed, double clipRatio) {
        this.clipRatio = clipRatio;
        this.rng = new Random(seed);
    }

    /**
     * Compute group advantages.
     */
    public GRPOGroup computeGroup(List<Double> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return new GRPOGroup(new java.util.ArrayList<>(),
                                  new java.util.ArrayList<>(), 0.0, 0.0);
        }
        int n = rewards.size();
        double mean = 0;
        for (double r : rewards) mean += r;
        mean /= n;
        double var = 0;
        for (double r : rewards) {
            double d = r - mean;
            var += d * d;
        }
        var /= n;
        double std = Math.sqrt(var + 1e-9);
        java.util.List<Double> advantages = new java.util.ArrayList<>();
        for (double r : rewards) {
            advantages.add((r - mean) / std);
        }
        return new GRPOGroup(rewards, advantages, mean, std);
    }

    /**
     * Compute GRPO loss (clipped).
     */
    public double loss(double oldProb, double newProb, double advantage) {
        double ratio = newProb / (oldProb + 1e-9);
        double clipped = Math.max(1 - clipRatio, Math.min(1 + clipRatio, ratio));
        return -Math.min(ratio * advantage, clipped * advantage);
    }

    /**
     * Train on a batch of profile groups.
     */
    public double train(List<List<Double>> rewardGroups, int epochs) {
        if (rewardGroups == null || rewardGroups.isEmpty() || epochs < 1) return 0.0;
        double totalLoss = 0;
        for (int step = 0; step < epochs; step++) {
            double epochLoss = 0;
            int count = 0;
            for (List<Double> group : rewardGroups) {
                GRPOGroup g = computeGroup(group);
                for (Double adv : g.advantages()) {
                    double oldProb = 0.5;
                    double newProb = 0.5 + adv * 0.01;
                    epochLoss += loss(oldProb, newProb, adv);
                    count++;
                }
            }
            if (count > 0) totalLoss += epochLoss / count;
        }
        return totalLoss / epochs;
    }

    public double clipRatio() { return clipRatio; }
}
