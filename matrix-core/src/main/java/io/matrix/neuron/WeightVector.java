package io.matrix.neuron;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * Priority weights for neuron inputs. {@code w_i ∈ {1,2,3}}.
 *
 * <p>Weight 3 = highest priority (evaluated first in decision tree).
 * Weight 1 = lowest priority.
 *
 * <p>Ref: L1_MPDT_neuron.md §2.1
 */
public final record WeightVector(int[] weights) {

    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 3;

    public WeightVector {
        Objects.requireNonNull(weights, "weights");
        weights = weights.clone();
        for (int w : weights) {
            if (w < MIN_WEIGHT || w > MAX_WEIGHT) {
                throw new IllegalArgumentException(
                        "Weight must be " + MIN_WEIGHT + "-" + MAX_WEIGHT + ", got " + w);
            }
        }
    }

    /**
     * Creates a uniform weight vector where every input has weight 2.
     *
     * @param k number of inputs, must be ≥ 1
     * @return uniform weight vector
     */
    public static WeightVector uniform(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be ≥ 1, got " + k);
        }
        int[] w = new int[k];
        Arrays.fill(w, 2);
        return new WeightVector(w);
    }

    /**
     * Creates a random weight vector with values in {@code [1, 3]}.
     *
     * @param k  number of inputs, must be ≥ 1
     * @param rng random number generator (seeded for reproducibility)
     * @return random weight vector
     */
    public static WeightVector random(int k, Random rng) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be ≥ 1, got " + k);
        }
        Objects.requireNonNull(rng, "rng");
        int[] w = new int[k];
        for (int i = 0; i < k; i++) {
            w[i] = MIN_WEIGHT + rng.nextInt(MAX_WEIGHT - MIN_WEIGHT + 1);
        }
        return new WeightVector(w);
    }

    /**
     * Creates a random weight vector using {@link java.util.concurrent.ThreadLocalRandom}.
     *
     * @param k number of inputs
     * @return random weight vector
     */
    public static WeightVector random(int k) {
        return random(k, new Random());
    }

    /**
     * Returns the weight for the given input index.
     *
     * @param inputIndex zero-based input index
     * @return weight value in {@code [1, 3]}
     */
    public int weight(int inputIndex) {
        if (inputIndex < 0 || inputIndex >= weights.length) {
            throw new IndexOutOfBoundsException(
                    "inputIndex " + inputIndex + " out of [0, " + weights.length + ")");
        }
        return weights[inputIndex];
    }

    /**
     * Returns the number of inputs (length of the weight vector).
     *
     * @return input count
     */
    public int size() {
        return weights.length;
    }

    /**
     * Returns input indices sorted by weight descending (highest priority first).
     *
     * <p>When two inputs share the same weight, the lower index comes first
     * (stable sort).
     *
     * @return array of input indices in priority order
     */
    public int[] priorityOrder() {
        return IntStream.range(0, weights.length)
                .boxed()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(weights[b], weights[a]);
                    return cmp != 0 ? cmp : Integer.compare(a, b);
                })
                .mapToInt(Integer::intValue)
                .toArray();
    }

    /**
     * Returns a defensive copy of the underlying weight array.
     *
     * @return weight array copy
     */
    public int[] toArray() {
        return weights.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WeightVector other)) return false;
        return Arrays.equals(weights, other.weights);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(weights);
    }

    @Override
    public String toString() {
        return "WeightVector" + Arrays.toString(weights);
    }
}
