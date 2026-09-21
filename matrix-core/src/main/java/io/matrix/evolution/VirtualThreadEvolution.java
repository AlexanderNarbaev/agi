package io.matrix.evolution;

import io.matrix.neuron.TruthTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Evolution loop accelerated with virtual threads for parallel fitness evaluation.
 * 
 * Uses Project Loom virtual threads to evaluate hundreds of chromosomes
 * concurrently without thread pool exhaustion.
 */
public class VirtualThreadEvolution {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadEvolution.class);

    /**
     * Evaluate fitness for all chromosomes in parallel using virtual threads.
     */
    public List<double[]> evaluateParallel(List<TruthTable> chromosomes,
                                           java.util.function.Function<TruthTable, Double> fitnessFn) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Double>> futures = new ArrayList<>();

            for (TruthTable tt : chromosomes) {
                futures.add(executor.submit(() -> fitnessFn.apply(tt)));
            }

            List<double[]> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    double fitness = futures.get(i).get();
                    results.add(new double[]{i, fitness});
                } catch (Exception e) {
                    log.warn("Fitness evaluation failed for chromosome {}: {}", i, e.getMessage());
                    results.add(new double[]{i, 0.0});
                }
            }
            return results;
        }
    }

    /**
     * Evolve population using virtual threads.
     */
    public List<TruthTable> evolve(List<TruthTable> population,
                                   java.util.function.Function<TruthTable, Double> fitnessFn,
                                   int generations,
                                   int k) {
        List<TruthTable> current = new ArrayList<>(population);

        for (int gen = 0; gen < generations; gen++) {
            // Evaluate fitness in parallel
            List<double[]> fitness = evaluateParallel(current, fitnessFn);

            // Select top performers
            fitness.sort((a, b) -> Double.compare(b[1], a[1]));
            List<TruthTable> next = new ArrayList<>();
            int selectCount = Math.max(2, current.size() / 2);
            for (int i = 0; i < selectCount; i++) {
                int idx = (int) fitness.get(i)[0];
                next.add(current.get(idx));
            }

            // Mutate to fill population
            while (next.size() < current.size()) {
                TruthTable parent = next.get((int) (Math.random() * next.size()));
                next.add(mutate(parent, k));
            }

            current = next;
        }

        return current;
    }

    private TruthTable mutate(TruthTable tt, int k) {
        // Create mutated copy by flipping random input-output pairs
        BitSet bits = (BitSet) tt.table().clone();
        int size = 1 << k;
        for (int i = 0; i < size; i++) {
            if (Math.random() < 0.1) {
                bits.flip(i);
            }
        }
        return TruthTable.of(k, bits);
    }
}
