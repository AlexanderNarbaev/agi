package io.matrix.neuron;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DESIGN-47 — Gillespie Stochastic Simulation Algorithm (1976).
 * Pure function (CONSTITUTION I) with seeded RNG.
 *
 * <p>API: each reaction has a delta[] indexed by population —
 * delta[i] is the change to population[i] when the reaction fires.
 * For 1-population decay, reaction is {delta: {-1}, rate: k}.
 */
public final class GillespieSimulator {

    public record Reaction(String name, int[] delta, double rate) {}

    public record Trajectory(
            List<Double> times,
            List<Integer> reactionIndices,
            int[] finalPopulation
    ) {}

    private GillespieSimulator() {}

    public static Trajectory simulate(int[] initialPopulation,
                                      List<Reaction> reactions,
                                      double tMax, int popMax,
                                      long seed) {
        if (initialPopulation == null || reactions == null) {
            throw new IllegalArgumentException("null");
        }
        int nPop = initialPopulation.length;
        int nRxn = reactions.size();
        int[] population = initialPopulation.clone();
        Random rng = new Random(seed);
        List<Double> times = new ArrayList<>();
        List<Integer> reactionIndices = new ArrayList<>();
        double t = 0;
        for (int event = 0; event < popMax; event++) {
            // Compute propensities
            double[] a = new double[nRxn];
            double a0 = 0;
            for (int j = 0; j < nRxn; j++) {
                a[j] = propensity(reactions.get(j), population);
                a0 += a[j];
            }
            if (a0 <= 0) break;
            // Sample waiting time
            double r1 = rng.nextDouble();
            if (r1 == 0) r1 = 1e-300;
            double tau = -Math.log(r1) / a0;
            t += tau;
            if (t > tMax) break;
            // Sample reaction index
            double r2 = rng.nextDouble() * a0;
            double cum = 0;
            int chosen = nRxn - 1;
            for (int j = 0; j < nRxn; j++) {
                cum += a[j];
                if (r2 < cum) {
                    chosen = j;
                    break;
                }
            }
            // Update population
            for (int i = 0; i < nPop; i++) {
                population[i] += reactions.get(chosen).delta()[i];
                if (population[i] < 0) population[i] = 0;
            }
            times.add(t);
            reactionIndices.add(chosen);
        }
        return new Trajectory(times, reactionIndices, population);
    }

    /**
     * Mass-action propensity: k · Π(population[i] for i in reactants).
     * For decay (-1), the rate is just k * pop[i] (linear).
     * For birth (+1), the rate is just k (constant).
     */
    private static double propensity(Reaction rxn, int[] population) {
        double rate = rxn.rate();
        for (int i = 0; i < population.length; i++) {
            int delta = rxn.delta()[i];
            if (delta == -1) {
                // First-order decay
                rate *= population[i];
            } else if (delta == 1) {
                // Zero-order birth (no reactants)
                // rate unchanged
            } else if (delta == 2) {
                // Second-order: rate = k * pop * (pop-1)
                rate *= population[i] * (population[i] - 1);
            } else if (delta == -2) {
                // Second-order decay: rate = k * pop * (pop-1)
                rate *= population[i] * (population[i] - 1);
            }
        }
        return rate;
    }
}
