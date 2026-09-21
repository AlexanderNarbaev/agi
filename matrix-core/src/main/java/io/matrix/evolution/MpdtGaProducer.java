package io.matrix.evolution;

import io.matrix.bir.ClauseSetForm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MPDT-GA producer — genetic-algorithm baseline for EXP-002 (H-002):
 * evolves a population of DNF clause sets on identical binary input to
 * compete against {@code io.matrix.tsetlin.TsetlinTrainer}.
 *
 * <p>Genome: per-clause pair of masks (pos, neg) packed in a single long
 * (arity ≤ 20 per CONSTITUTION II). Fitness = train accuracy with an MDL
 * pressure term (λ=0.1) penalizing literal count. Deterministic: seeded
 * Random, elitism (top 25%) + tournament selection, per-clause crossover.
 */
public final class MpdtGaProducer {

    private final int inputBits;
    private final int clauses;
    private final int populationSize;
    private final Random rng;

    private long[][] best;
    private double lastTrainAcc = -1;

    public MpdtGaProducer(int inputBits, int clauses, int populationSize, long seed) {
        if (inputBits < 1 || inputBits > 20) {
            throw new IllegalArgumentException("inputBits in 1..20");
        }
        if (clauses < 1 || populationSize < 4) {
            throw new IllegalArgumentException("clauses ≥ 1, populationSize ≥ 4");
        }
        this.inputBits = inputBits;
        this.clauses = clauses;
        this.populationSize = populationSize;
        this.rng = new Random(seed);
    }

    private long mask() {
        return inputBits == 64 ? -1L : (1L << inputBits) - 1;
    }

    /** Clause fires when all pos bits are set and no neg bit is set. */
    private static boolean fires(long pos, long neg, long x, long mask) {
        if (pos == 0 && neg == 0) return false;
        boolean posOk = pos == 0 || ((x & mask) & pos) == pos;
        boolean negClear = neg == 0 || ((x & mask) & neg) == 0;
        return posOk && negClear;
    }

    private boolean predictGenome(long[][] genome, long x) {
        for (long[] clause : genome) {
            if (fires(clause[0], clause[1], x, mask())) return true;
        }
        return false;
    }

    /** Trains with an elitist GA over packed one-word inputs. */
    public void trainBatch(long[] inputs, boolean[] labels, int generations) {
        if (generations < 1 || inputs.length != labels.length || inputs.length == 0) {
            throw new IllegalArgumentException("bad training arguments");
        }
        List<long[][]> pop = new ArrayList<>(populationSize);
        for (int p = 0; p < populationSize; p++) {
            long[][] g = new long[clauses][2];
            for (int c = 0; c < clauses; c++) {
                g[c][0] = randomMask();
                g[c][1] = rng.nextBoolean() ? randomMask() : 0L;
            }
            pop.add(g);
        }

        for (int gen = 0; gen < generations; gen++) {
            double[][] fits = new double[populationSize][2];
            Integer[] order = new Integer[populationSize];
            for (int i = 0; i < populationSize; i++) {
                fits[i] = fitness(pop.get(i), inputs, labels);
                order[i] = i;
            }
            java.util.Arrays.sort(order,
                    (a, b) -> Double.compare(fits[b][0], fits[a][0]));

            List<long[][]> next = new ArrayList<>(populationSize);
            int elite = Math.max(1, populationSize / 4);
            for (int i = 0; i < elite && next.size() < populationSize; i++) {
                next.add(pop.get(order[i]));
            }
            while (next.size() < populationSize) {
                int a = tournament(fits);
                int b = tournament(fits);
                next.add(crossover(pop.get(a), pop.get(b)));
            }
            pop = next;
        }

        double bestFit = -1;
        int bestIdx = 0;
        for (int i = 0; i < populationSize; i++) {
            double[] f = fitness(pop.get(i), inputs, labels);
            if (f[0] > bestFit) {
                bestFit = f[0];
                bestIdx = i;
            }
        }
        this.best = pop.get(bestIdx);
        // Accuracy of the best individual on the same train set:
        int hit = 0;
        for (int i = 0; i < inputs.length; i++) {
            if (predictGenome(this.best, inputs[i]) == labels[i]) hit++;
        }
        this.lastTrainAcc = hit / (double) inputs.length;
        this.trainedFlag = true;
    }

    private boolean trainedFlag;

    private double[] fitness(long[][] genome, long[] inputs, boolean[] labels) {
        int hit = 0;
        int literals = 0;
        for (long[] clause : genome) {
            literals += Long.bitCount(clause[0]) + Long.bitCount(clause[1]);
        }
        int maxLiterals = Math.max(1, genome.length * 2 * inputBits);
        for (int i = 0; i < inputs.length; i++) {
            if (predictGenome(genome, inputs[i]) == labels[i]) hit++;
        }
        double acc = hit / (double) inputs.length;
        return new double[]{acc - 0.1 * literals / maxLiterals, acc};
    }

    private int tournament(double[][] fits) {
        int a = rng.nextInt(fits.length);
        int b = rng.nextInt(fits.length);
        return fits[a][0] >= fits[b][0] ? a : b;
    }

    private long[][] crossover(long[][] p1, long[][] p2) {
        long[][] child = new long[clauses][2];
        int point = rng.nextInt(clauses + 1);
        for (int c = 0; c < clauses; c++) {
            child[c][0] = mutate(c < point ? p1[c][0] : p2[c][0]);
            child[c][1] = mutate(c < point ? p1[c][1] : p2[c][1]);
        }
        return child;
    }

    private long mutate(long word) {
        if (rng.nextDouble() < 1.0 / inputBits) {
            word ^= 1L << rng.nextInt(inputBits);
        }
        return word & mask();
    }

    private long randomMask() {
        long m = 0;
        for (int b = 0; b < inputBits; b++) {
            if (rng.nextBoolean()) m |= 1L << b;
        }
        return m;
    }

    public boolean predict(long packed) {
        requireTrained();
        return predictGenome(best, packed);
    }

    /** Exports the evolved DNF as CLAUSESET (artifact-size metric source). */
    public ClauseSetForm toDecisionClauseSet(String provenance) {
        requireTrained();
        List<ClauseSetForm.Clause> out = new ArrayList<>(clauses);
        for (long[] clause : best) {
            if (clause[0] == 0 && clause[1] == 0) continue;
            out.add(new ClauseSetForm.Clause(
                    new long[]{clause[0]}, new long[]{clause[1]}));
        }
        String prov = provenance + ":ga";
        if (out.isEmpty()) {
            return ClauseSetForm.lossy(inputBits,
                    List.of(new ClauseSetForm.Clause(new long[]{1L}, new long[]{1L})),
                    prov, lastTrainAcc <= 0 ? 0.0 : lastTrainAcc);
        }
        return ClauseSetForm.lossy(inputBits, out, prov,
                lastTrainAcc <= 0 ? 0.0 : lastTrainAcc);
    }

    /** Number of stored literals in the best genome (artifact size metric). */
    public int literalCount() {
        requireTrained();
        int literals = 0;
        for (long[] clause : best) {
            literals += Long.bitCount(clause[0]) + Long.bitCount(clause[1]);
        }
        return literals;
    }

    private void requireTrained() {
        if (!trainedFlag) {
            throw new IllegalStateException("producer is not trained");
        }
    }
}
