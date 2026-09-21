package io.matrix.pilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RUN 166 — PilotGridWorld (minimal agent + environment).
 *
 * <p>A 4-neuron agent that learns to navigate a small grid. Spec
 * L13 §2: "4–8-neuron agent, GA with elitism."
 *
 * <p>Agent input: 4-bit sensor (N/S/E/W cell types)
 * Agent output: 4-bit action (move N/S/E/W)
 *
 * <p>Genome = 4×4 boolean weights + 4-bit thresholds = 20 bits total
 * (well within K_MAX=20 per CONSTITUTION II).
 *
 * <p>Determinism: we use a fixed-seed Random so the GA produces
 * reproducible evolution traces.
 */
public final class PilotGridWorld {

    /** Genome = 4 thresholds + 4×4 weights = 20 bits. */
    public static final int GENOME_BITS = 20;

    public static final int ACTIONS = 4; // N, S, E, W
    public static final int SENSORS = 4; // N, S, E, W cell types

    public record Genome(boolean[] bits) {}

    public static Genome randomGenome(long seed) {
        Random rng = new Random(seed);
        boolean[] bits = new boolean[GENOME_BITS];
        for (int i = 0; i < GENOME_BITS; i++) bits[i] = rng.nextBoolean();
        return new Genome(bits);
    }

    /**
     * Read thresholds (first 4 bits) and weights (next 16 bits)
     * from a genome.
     */
    public static Agent decode(Genome g) {
        int[] thresholds = new int[ACTIONS];
        for (int i = 0; i < ACTIONS; i++) {
            thresholds[i] = g.bits()[i] ? 3 : 2; // 2 or 3 out of 4
        }
        int[][] weights = new int[ACTIONS][SENSORS];
        for (int a = 0; a < ACTIONS; a++) {
            for (int s = 0; s < SENSORS; s++) {
                int idx = 4 + a * SENSORS + s;
                weights[a][s] = g.bits()[idx] ? 1 : 0;
            }
        }
        return new Agent(weights, thresholds);
    }

    /**
     * Pick the action whose weighted sum of sensors exceeds its
     * threshold. Ties broken by action index (lower wins).
     */
    public static int decide(Agent agent, int[] sensors) {
        int best = -1;
        int bestSum = Integer.MIN_VALUE;
        for (int a = 0; a < ACTIONS; a++) {
            int sum = 0;
            for (int s = 0; s < SENSORS; s++) {
                sum += agent.weights()[a][s] * sensors[s];
            }
            if (sum > bestSum) {
                bestSum = sum;
                best = a;
            } else if (sum == bestSum && best == -1) {
                best = a;
            }
        }
        return best;
    }

    /** Compute a fitness score from a trajectory of (state, action, reward). */
    public static double fitness(List<Double> rewards) {
        double sum = 0;
        for (double r : rewards) sum += r;
        return sum;
    }

    /** One generation of GA-style elitism selection. */
    public static List<Genome> evolve(List<Genome> population,
                                      List<Double> fitnesses,
                                      long seed) {
        if (population.isEmpty()) return new ArrayList<>();
        // Sort by fitness descending
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < population.size(); i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(fitnesses.get(b), fitnesses.get(a)));
        // Elitism: keep top half
        List<Genome> next = new ArrayList<>();
        int half = population.size() / 2;
        for (int i = 0; i < half; i++) {
            next.add(population.get(idx.get(i)));
        }
        // Crossover + mutation to fill
        Random rng = new Random(seed);
        while (next.size() < population.size()) {
            Genome a = next.get(rng.nextInt(next.size()));
            Genome b = next.get(rng.nextInt(next.size()));
            boolean[] child = new boolean[GENOME_BITS];
            for (int i = 0; i < GENOME_BITS; i++) {
                child[i] = rng.nextBoolean() ? a.bits()[i] : b.bits()[i];
            }
            // Mutation: 5% per bit
            for (int i = 0; i < GENOME_BITS; i++) {
                if (rng.nextDouble() < 0.05) child[i] = !child[i];
            }
            next.add(new Genome(child));
        }
        return next;
    }

    public record Agent(int[][] weights, int[] thresholds) {}
}
