package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 432 — Reservoir sampling (Algorithm R, Vitter 1985).
 * <p>Selects {@code k} items uniformly at random from a stream of unknown
 * length in a single pass with {@code O(n)} time and {@code O(k)} space.
 * Pure function. Caller-supplied {@link Random}. CONSTITUTION I-safe.
 */
public final class ReservoirSampler {

    private ReservoirSampler() {}

    /**
     * Reservoir sampling of {@code reservoir}. Items arriving after the
     * first {@code k} are probabilistically inserted, displacing a random
     * existing item with probability {@code k / streamSize}.
     */
    public static void sample(long[] source, long[] reservoir, Random rng) {
        int k = reservoir.length;
        long seenSoFar = k;
        // Fill initial reservoir from first k elements
        System.arraycopy(source, 0, reservoir, 0, k);
        // Iterate over rest, accept each with probability k / seenSoFar+1
        for (int i = k; i < source.length; i++) {
            seenSoFar++;
            long j = Math.floorMod(rng.nextLong(), seenSoFar);
            if (j < k) reservoir[(int) j] = source[i];
        }
    }
}
