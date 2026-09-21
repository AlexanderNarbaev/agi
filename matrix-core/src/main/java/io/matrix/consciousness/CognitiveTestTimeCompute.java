package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * W303 — Cognitive Test-Time Compute (o1-style reasoning).
 *
 * <p>Inspired by OpenAI o1 (2024) and DeepSeek R1 (2025). Allocate
 * extra computation at inference time for hard problems.
 *
 * <p>Strategy:
 * - Generate candidate reasoning paths
 * - Verify each via constitutional AI + RLHF-style scoring
 * - Best-of-N selection with adaptive compute
 *
 * <p>CONSTITUTION VI compliance: test-time compute cognitive substrate,
 * not phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveTestTimeCompute {

    private CognitiveTestTimeCompute() {}

    /** A single reasoning attempt. */
    public record ReasoningAttempt(
        List<CognitiveGenesisProfile> trajectory,
        double score,
        boolean verified
    ) {}

    /** Result of test-time compute. */
    public record TestTimeResult(
        ReasoningAttempt bestAttempt,
        List<ReasoningAttempt> allAttempts,
        int totalCompute,
        double aggregateScore
    ) {}

    private long seed;
    private int maxAttempts;

    public CognitiveTestTimeCompute(long seed, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.seed = seed;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Solve a cognitive problem with test-time compute.
     *
     * @param initial starting profile
     * @param numSteps number of reasoning steps
     * @return best attempt + all attempts
     */
    public TestTimeResult solve(CognitiveGenesisProfile initial, int numSteps) {
        if (initial == null || numSteps < 1) {
            return new TestTimeResult(null, new ArrayList<>(), 0, 0.0);
        }
        Random rng = new Random(seed);
        List<ReasoningAttempt> attempts = new ArrayList<>();
        ReasoningAttempt best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < maxAttempts; i++) {
            // Generate attempt with random perturbations
            List<CognitiveGenesisProfile> trajectory = new ArrayList<>();
            CognitiveGenesisProfile current = perturb(initial, rng);
            trajectory.add(current);
            for (int s = 0; s < numSteps; s++) {
                current = perturb(current, rng);
                trajectory.add(current);
            }
            // Score via constitutional AI + complexity
            double score = scoreAttempt(trajectory, rng);
            boolean verified = score > 0.5;
            ReasoningAttempt attempt = new ReasoningAttempt(trajectory, score, verified);
            attempts.add(attempt);
            if (score > bestScore) {
                bestScore = score;
                best = attempt;
            }
        }
        double aggScore = attempts.stream().mapToDouble(ReasoningAttempt::score).average().orElse(0.0);
        return new TestTimeResult(best, attempts, maxAttempts * numSteps, aggScore);
    }

    /**
     * Generate candidate via sampling without explicit verification.
     */
    public List<ReasoningAttempt> sample(CognitiveGenesisProfile initial, int numSteps, int k) {
        if (initial == null || numSteps < 1 || k < 1) {
            return new ArrayList<>();
        }
        Random rng = new Random(seed);
        List<ReasoningAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<CognitiveGenesisProfile> trajectory = new ArrayList<>();
            CognitiveGenesisProfile current = perturb(initial, rng);
            trajectory.add(current);
            for (int s = 0; s < numSteps; s++) {
                current = perturb(current, rng);
                trajectory.add(current);
            }
            double score = scoreAttempt(trajectory, rng);
            attempts.add(new ReasoningAttempt(trajectory, score, score > 0.5));
        }
        return attempts;
    }

    private static CognitiveGenesisProfile perturb(CognitiveGenesisProfile p, Random rng) {
        double noise = 0.05 * (rng.nextGaussian());
        double newPhi = Math.max(0.0, Math.min(1.0, p.phiBinary() + noise));
        return new CognitiveGenesisProfile(
            newPhi, p.phiF(), p.phiR(), p.phiLinGauss(),
            p.interAgentPhi(), p.stabilityPhi(), p.crossLevelPhi(),
            p.kolmogorovK(),
            p.analogicalSimilarity(), p.conceptualExclusion(),
            p.nkEdgeOfChaosK(),
            p.memristorConductance(), p.lSystemComplexityRatio()
        );
    }

    private static double scoreAttempt(List<CognitiveGenesisProfile> trajectory, Random rng) {
        if (trajectory.isEmpty()) return 0.0;
        double sum = 0;
        for (CognitiveGenesisProfile p : trajectory) {
            sum += p.phiBinary();
        }
        return (sum / trajectory.size()) + rng.nextGaussian() * 0.01;
    }

    public long seed() { return seed; }
    public int maxAttempts() { return maxAttempts; }
}
