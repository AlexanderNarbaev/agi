package io.matrix.pilot;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 167 — PilotGridWorld EXP.
 *
 * <p>Real workload: 50 generations of GA. Verify fitness grows
 * with elitism+crossover+mutation on a deterministic toy env.
 */
@Tag("exp")
class Exp167GridWorldEvoTest {

    @Test
    void fiftyGenerationsOfGA() {
        long seed = 0x5A5A5A5AL;
        Random rng = new Random(seed);

        // Initial population
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        List<Double> fits = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pop.add(PilotGridWorld.randomGenome(rng.nextLong()));
            fits.add(0.0);
        }

        // Toy env: random sensor states, fitness = reward sum
        double[] gens = new double[50];
        double bestFitness = 0;
        for (int gen = 0; gen < 50; gen++) {
            // Evaluate
            for (int i = 0; i < pop.size(); i++) {
                fits.set(i, evaluate(pop.get(i)));
            }
            bestFitness = max(fits);
            gens[gen] = bestFitness;
            // Evolve
            pop = PilotGridWorld.evolve(pop, fits, seed + gen);
        }
        System.out.printf("[GRID-EVO-50] best=%s, gens[0]=%s, gens[49]=%s%n",
                bestFitness, gens[0], gens[49]);
        // At least something survived
        assertThat(bestFitness).isGreaterThan(0);
        // Sanity: deterministic
        assertThat(gens[49]).isGreaterThan(gens[0]);
    }

    @Test
    void deterministicReplay() {
        // Run twice with same seed — same trajectory
        long seed = 0x5A5A5A5AL;
        double fitA = runWithSeed(seed);
        double fitB = runWithSeed(seed);
        assertThat(fitA).isEqualTo(fitB);
        System.out.printf("[GRID-DETERM] %s == %s%n", fitA, fitB);
    }

    private static double evaluate(PilotGridWorld.Genome g) {
        // Toy: minimize Hamming distance to "all true" weighted by threshold=3
        PilotGridWorld.Agent a = PilotGridWorld.decode(g);
        Random envRng = new Random(g.bits().hashCode());  // env derived from genome
        double score = 0;
        for (int step = 0; step < 20; step++) {
            int[] sensors = {envRng.nextInt(2), envRng.nextInt(2),
                              envRng.nextInt(2), envRng.nextInt(2)};
            int action = PilotGridWorld.decide(a, sensors);
            // Reward: action 0 (N) when sensor N = 1
            score += (action == 0 && sensors[0] == 1) ? 1.0 : 0;
        }
        return score;
    }

    private static double max(List<Double> l) {
        double m = Double.NEGATIVE_INFINITY;
        for (double d : l) if (d > m) m = d;
        return m;
    }

    private static double runWithSeed(long seed) {
        Random rng = new Random(seed);
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        List<Double> fits = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            pop.add(PilotGridWorld.randomGenome(rng.nextLong()));
            fits.add(0.0);
        }
        double best = 0;
        for (int gen = 0; gen < 50; gen++) {
            for (int i = 0; i < pop.size(); i++) {
                fits.set(i, evaluate(pop.get(i)));
            }
            best = max(fits);
            pop = PilotGridWorld.evolve(pop, fits, seed + gen);
        }
        return best;
    }
}
