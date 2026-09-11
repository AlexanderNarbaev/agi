package io.matrix.neuron;

/**
 * DESIGN-40 — Kauffman Boolean Network (random Boolean network).
 * Each node has K inputs and a random Boolean function.
 * Pure function (CONSTITUTION I).
 */
public final class KauffmanNetwork {

    private KauffmanNetwork() {}

    /**
     * One synchronous step: each node updates based on its K inputs.
     * @param state current state (n nodes)
     * @param functions for each node: array of 2^K truth table entries
     * @param inputs for each node: K indices into state
     * @return new state (input state unchanged)
     */
    public static boolean[] step(boolean[] state, int[][] functions,
                                 int[][] inputs) {
        if (state == null || functions == null || inputs == null) {
            throw new IllegalArgumentException("null");
        }
        if (functions.length != inputs.length
                || functions.length != state.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        int n = state.length;
        boolean[] newState = new boolean[n];
        for (int i = 0; i < n; i++) {
            int K = inputs[i].length;
            int idx = 0;
            for (int k = 0; k < K; k++) {
                if (state[inputs[i][k]]) idx |= (1 << (K - 1 - k));
            }
            newState[i] = functions[i][idx] != 0;
        }
        return newState;
    }

    /** Build random Kauffman network. */
    public static KauffmanSpec random(int n, int K, long seed) {
        if (n <= 0 || K <= 0 || K > 16) {
            throw new IllegalArgumentException("n>0, 0<K<=16");
        }
        java.util.Random rng = new java.util.Random(seed);
        int[][] functions = new int[n][1 << K];
        int[][] inputs = new int[n][K];
        for (int i = 0; i < n; i++) {
            // Random truth table
            for (int j = 0; j < (1 << K); j++) {
                functions[i][j] = rng.nextBoolean() ? 1 : 0;
            }
            // K random distinct inputs
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (int k = 0; k < K; k++) {
                int idx;
                do {
                    idx = rng.nextInt(n);
                } while (used.contains(idx));
                used.add(idx);
                inputs[i][k] = idx;
            }
        }
        return new KauffmanSpec(functions, inputs);
    }

    public record KauffmanSpec(int[][] functions, int[][] inputs) {}
}
