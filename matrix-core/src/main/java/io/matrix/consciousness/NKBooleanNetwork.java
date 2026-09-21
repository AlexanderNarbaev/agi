package io.matrix.consciousness;

import java.util.Random;

/**
 * W108 — NK Boolean network (Kauffman 1969, 1993).
 *
 * <p>An NK Boolean network models a system of N binary "genes", each
 * with K inputs from other genes. The dynamics are deterministic
 * discrete-time: at each step, each gene's new state is a Boolean
 * function of its K inputs.
 *
 * <p>Kauffman's central finding (1969, 1993): for K=1 the network is
 * frozen/ordered; for K=N-1 the network is chaotic. At K≈2 (the
 * "edge of chaos"), the system is complex: it has many short attractors,
 * supports computation, and is robust yet adaptable.
 *
 * <p>This relates to MATRIX in two ways:
 * <ol>
 *   <li>Wiring weights in the brain cycle define an effective K;
 *       Φ measurements may peak at the edge of chaos (H-092 hypothesis).</li>
 *   <li>The attractor structure of NK networks is a benchmark for
 *       integration metric behaviors (H-093 hypothesis).</li>
 * </ol>
 *
 * <p>Cross-disciplinary synthesis (META-R1 R-D): Spelke's core knowledge
 * (object/agent/number) corresponds to attractors at the edge of chaos
 * — they are robust yet learnable. Kauffman's NK model gives us a
 * formal substrate for testing these ideas.
 *
 * <p>CONSTITUTION VI compliance: a model of dynamical complexity,
 * not a phenomenal consciousness claim.
 */
public final class NKBooleanNetwork {

    private NKBooleanNetwork() {}

    /**
     * Compute the attractor (cycle) that the network reaches from a given
     * initial state. Returns the cycle length and the states in the cycle.
     *
     * @param N network size (number of genes)
     * @param K connectivity (each gene has K inputs)
     * @param seed RNG seed for deterministic rule generation
     * @param initialState starting state (length N)
     * @param maxSteps maximum steps before giving up
     * @return CycleRecord with cycle length and the states in the cycle
     */
    public static CycleRecord findAttractor(int N, int K, long seed, boolean[] initialState, int maxSteps) {
        if (N < 1) throw new IllegalArgumentException("N must be ≥ 1");
        if (K < 0 || K >= N) throw new IllegalArgumentException("K must be in [0, N-1]");
        if (initialState.length != N) throw new IllegalArgumentException("initial state must have length N");

        // Generate random Boolean functions for each gene.
        // Each gene has K inputs, so its truth table has 2^K entries.
        Random rng = new Random(seed);
        int[][] truthTables = new int[N][1 << K];
        for (int g = 0; g < N; g++) {
            for (int i = 0; i < (1 << K); i++) {
                truthTables[g][i] = rng.nextInt(2);
            }
        }

        // Generate random wiring: each gene gets K inputs from other genes.
        int[][] wiring = new int[N][K];
        for (int g = 0; g < N; g++) {
            // Generate K distinct gene indices != g
            boolean[] used = new boolean[N];
            used[g] = true;
            int count = 0;
            while (count < K) {
                int idx = rng.nextInt(N);
                if (!used[idx]) {
                    wiring[g][count++] = idx;
                    used[idx] = true;
                }
            }
        }

        // Simulate dynamics
        boolean[] state = initialState.clone();
        // Use a hash map for cycle detection
        java.util.Map<Long, Integer> visited = new java.util.HashMap<>();
        long visitedHash = hashState(state);
        for (int step = 0; step < maxSteps; step++) {
            if (visited.containsKey(visitedHash)) {
                int cycleStart = visited.get(visitedHash);
                int cycleLength = step - cycleStart;
                return new CycleRecord(cycleLength, cycleStart, step, false);
            }
            visited.put(visitedHash, step);

            // Compute next state
            boolean[] next = new boolean[N];
            for (int g = 0; g < N; g++) {
                int addr = 0;
                for (int j = 0; j < K; j++) {
                    addr = (addr << 1) | (state[wiring[g][j]] ? 1 : 0);
                }
                next[g] = truthTables[g][addr] == 1;
            }
            state = next;
            visitedHash = hashState(state);
        }
        return new CycleRecord(-1, -1, maxSteps, true);
    }

    /**
     * The "edge of chaos" K for a given N. Kauffman's empirical result:
     * for random Boolean networks, K≈2 marks the transition.
     */
    public static int edgeOfChaosK(int N) {
        return Math.min(2, N - 1);
    }

    /** Hash a boolean state to a long for use as a map key. */
    private static long hashState(boolean[] state) {
        long h = 0;
        // Use Arrays.hashCode-like fold but pack 64 bits
        for (int i = 0; i < state.length; i++) {
            h = h * 31 + (state[i] ? 1 : 0);
        }
        return h;
    }

    public record CycleRecord(int cycleLength, int cycleStart, int totalSteps, boolean hitMaxSteps) {
        public boolean converged() {
            return cycleLength >= 0;
        }
    }
}
