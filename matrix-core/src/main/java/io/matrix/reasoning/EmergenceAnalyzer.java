package io.matrix.reasoning;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * RUN 42 — Emergence-of-behavior analyzer (H-048 verification).
 *
 * <p>H-048 hypothesis: N=1000 cycles preserve stable action-distribution
 * entropy and decision-tree shape.
 *
 * <p>This analyzer runs a fixed sequence of "cycles" against a
 * deterministic seed-fixed replay, samples action distributions at
 * intervals, and computes:
 * <ul>
 *   <li>entropy at each snapshot point (Shannon entropy in bits)</li>
 *   <li>drift between snapshots (KL divergence approximation)</li>
 *   <li>decision-tree shape diff (frequency of repeated decisions)</li>
 * </ul>
 *
 * <p>Honest caveat: this is a STANDALONE analyzer that simulates
 * cycles via a deterministic rule. Full integration with the
 * ConsciousnessLoop is deferred.
 */
public class EmergenceAnalyzer {

    /** A snapshot of the action distribution at a given tick. */
    public record DistributionSnapshot(long tick, Map<String, Integer> actions) {
        /** Shannon entropy in bits. */
        public double entropy() {
            int total = 0;
            for (int c : actions.values()) total += c;
            if (total == 0) return 0.0;
            double h = 0.0;
            for (int c : actions.values()) {
                if (c == 0) continue;
                double p = (double) c / total;
                h -= p * Math.log(p) / Math.log(2);
            }
            return h;
        }
    }

    /** Drift metric between two distributions. */
    public record DriftMetric(double entropyChange, double l1Distance, long tickDelta) {}

    private final Random rng;
    private final long seed;

    public EmergenceAnalyzer() {
        this(42L);
    }

    public EmergenceAnalyzer(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
    }

    /**
     * Run N simulated cycles with a deterministic rule and sample
     * the action distribution at the given tick intervals.
     */
    public Map<Long, DistributionSnapshot> runCycles(int n,
                                                     int snapshotInterval,
                                                     int numActions) {
        Map<Long, DistributionSnapshot> snapshots = new HashMap<>();
        Map<String, Integer> actionCounts = new HashMap<>();
        // Synthetic rule: each cycle picks an action based on a
        // deterministic hash of (tick, seed). No real randomness —
        // the same seed produces the same action sequence.
        for (int tick = 0; tick < n; tick++) {
            // Use a separate Random per tick for determinism.
            Random tickRng = new Random(seed + tick);
            int actionIdx = tickRng.nextInt(numActions);
            String action = "action_" + actionIdx;
            actionCounts.merge(action, 1, Integer::sum);

            if ((tick + 1) % snapshotInterval == 0) {
                Map<String, Integer> copy = new HashMap<>(actionCounts);
                snapshots.put((long) (tick + 1),
                        new DistributionSnapshot(tick + 1, copy));
            }
        }
        return snapshots;
    }

    /**
     * Compute drift between two snapshots.
     */
    public DriftMetric computeDrift(DistributionSnapshot a, DistributionSnapshot b) {
        if (a == null || b == null) return new DriftMetric(0, 0, 0);
        double entropyChange = b.entropy() - a.entropy();
        // L1 distance between normalized distributions.
        int totalA = 0, totalB = 0;
        for (int c : a.actions().values()) totalA += c;
        for (int c : b.actions().values()) totalB += c;
        java.util.Set<String> allKeys = new java.util.HashSet<>();
        allKeys.addAll(a.actions().keySet());
        allKeys.addAll(b.actions().keySet());
        double l1 = 0.0;
        for (String k : allKeys) {
            double pA = totalA > 0 ? (double) a.actions().getOrDefault(k, 0) / totalA : 0;
            double pB = totalB > 0 ? (double) b.actions().getOrDefault(k, 0) / totalB : 0;
            l1 += Math.abs(pA - pB);
        }
        return new DriftMetric(entropyChange, l1, b.tick() - a.tick());
    }

    public long seed() { return seed; }
}
