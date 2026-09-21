package io.matrix.pilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RUN 180 — PilotParameterSweep (GA hyperparameter tuning).
 *
 * <p>Sweeps mutation rate × population size, tracks best
 * fitness per configuration. Deterministic given fixed seeds.
 */
public final class PilotParameterSweep {

    public record Config(double mutationRate, int populationSize,
                         long seed) {}

    public record Result(Config config, double finalFitness,
                         int generations) {}

    public static List<Result> sweep(double[] mutationRates,
                                     int[] populationSizes,
                                     int generations,
                                     long baseSeed) {
        List<Result> results = new ArrayList<>();
        int idx = 0;
        for (double mr : mutationRates) {
            for (int ps : populationSizes) {
                long seed = baseSeed + idx;
                idx++;
                double fitness = runWithConfig(mr, ps, generations, seed);
                results.add(new Result(
                        new Config(mr, ps, seed),
                        fitness,
                        generations));
            }
        }
        return results;
    }

    private static double runWithConfig(double mutationRate,
                                        int populationSize,
                                        int generations,
                                        long seed) {
        Random rng = new Random(seed);
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            pop.add(PilotGridWorld.randomGenome(rng.nextLong()));
        }
        List<Double> fits = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) fits.add(0.0);
        for (int gen = 0; gen < generations; gen++) {
            for (int i = 0; i < pop.size(); i++) fits.set(i, score(pop.get(i)));
            pop = evolve(pop, fits, seed + gen, mutationRate, rng);
        }
        for (int i = 0; i < pop.size(); i++) fits.set(i, score(pop.get(i)));
        double best = 0;
        for (double f : fits) if (f > best) best = f;
        return best;
    }

    private static List<PilotGridWorld.Genome> evolve(
            List<PilotGridWorld.Genome> pop, List<Double> fitnesses,
            long seed, double mutationRate, Random rng) {
        // Elitism + crossover + custom mutation rate
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < pop.size(); i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(fitnesses.get(b), fitnesses.get(a)));
        List<PilotGridWorld.Genome> next = new ArrayList<>();
        int half = pop.size() / 2;
        for (int i = 0; i < half; i++) next.add(pop.get(idx.get(i)));
        while (next.size() < pop.size()) {
            PilotGridWorld.Genome a = next.get(rng.nextInt(next.size()));
            PilotGridWorld.Genome b = next.get(rng.nextInt(next.size()));
            boolean[] child = new boolean[PilotGridWorld.GENOME_BITS];
            for (int i = 0; i < PilotGridWorld.GENOME_BITS; i++) {
                child[i] = rng.nextBoolean() ? a.bits()[i] : b.bits()[i];
            }
            for (int i = 0; i < PilotGridWorld.GENOME_BITS; i++) {
                if (rng.nextDouble() < mutationRate) child[i] = !child[i];
            }
            next.add(new PilotGridWorld.Genome(child));
        }
        return next;
    }

    private static double score(PilotGridWorld.Genome g) {
        PilotGridWorld.Agent a = PilotGridWorld.decode(g);
        Random rng = new Random(g.bits().hashCode());
        double s = 0;
        for (int step = 0; step < 20; step++) {
            int[] sensors = {rng.nextInt(2), rng.nextInt(2),
                              rng.nextInt(2), rng.nextInt(2)};
            int action = PilotGridWorld.decide(a, sensors);
            s += (action == 0 && sensors[0] == 1) ? 1 : 0;
        }
        return s;
    }
}
