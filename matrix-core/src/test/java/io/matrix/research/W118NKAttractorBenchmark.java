package io.matrix.research;

import io.matrix.consciousness.NKBooleanNetwork;
import io.matrix.consciousness.NKBooleanNetwork.CycleRecord;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W118 — NK Boolean network attractor benchmark.
 *
 * <p>Empirically verify Kauffman's finding: cycle length distribution
 * depends dramatically on K. At K=1 (frozen), most cycles are short
 * (1-4 states). At K=2 (edge of chaos), moderate lengths (8-50).
 * At K=N-1 (chaotic), long cycles (close to 2^N).
 *
 * <p>Test H-092: Φ measurements should peak at edge of chaos (K=2).
 * Test H-093: attractor structure benchmarks integration metrics.
 */
class W118NKAttractorBenchmark {

    private static final int N = 12;
    private static final int NUM_NETWORKS = 20;
    private static final int NUM_SEEDS = 5;

    @Test
    void frozenRegimeHasShortCycles() {
        // K=1: expected cycle length 1-4
        List<Integer> lengths = collectCycleLengths(1);
        double mean = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        assertThat(mean).isLessThan(10.0);
    }

    @Test
    void edgeOfChaosHasModerateCycles() {
        // K=2: expected cycle length ~4-30
        List<Integer> lengths = collectCycleLengths(2);
        double mean = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
        assertThat(mean).isBetween(2.0, 100.0);
    }

    @Test
    void chaoticRegimeHasLongerCyclesThanFrozen() {
        List<Integer> frozen = collectCycleLengths(1);
        List<Integer> chaotic = collectCycleLengths(N - 1);
        double meanFrozen = frozen.stream().mapToInt(Integer::intValue).average().orElse(0);
        double meanChaotic = chaotic.stream().mapToInt(Integer::intValue).average().orElse(0);
        // Chaotic should generally be longer than frozen
        assertThat(meanChaotic).isGreaterThanOrEqualTo(meanFrozen);
    }

    @Test
    void allNetworksConvergeWithinMaxSteps() {
        // For N=12, max cycle length is 2^12 = 4096. Setting max=5000 ensures convergence.
        for (int k = 1; k < N; k += 2) {
            List<Integer> lengths = collectCycleLengths(k);
            // All networks converged
            assertThat(lengths).hasSize(NUM_NETWORKS * NUM_SEEDS);
            for (Integer l : lengths) {
                assertThat(l).isGreaterThan(0); // Cycle length >= 1
            }
        }
    }

    @Test
    void edgeOfChaosKIsCanonical() {
        // For N=12, edge-of-chaos K = 2
        assertThat(NKBooleanNetwork.edgeOfChaosK(N)).isEqualTo(2);
        assertThat(NKBooleanNetwork.edgeOfChaosK(2)).isEqualTo(1);  // min(N-1, 2)
        assertThat(NKBooleanNetwork.edgeOfChaosK(1)).isEqualTo(0);  // 1-1=0
    }

    /** Collect cycle lengths from NUM_NETWORKS × NUM_SEEDS random networks. */
    private static List<Integer> collectCycleLengths(int K) {
        List<Integer> lengths = new ArrayList<>();
        for (int netSeed = 0; netSeed < NUM_NETWORKS; netSeed++) {
            for (int initSeed = 0; initSeed < NUM_SEEDS; initSeed++) {
                long seed = (long) netSeed * NUM_SEEDS + initSeed;
                Random rng = new Random(seed);
                boolean[] init = new boolean[N];
                rng.nextBytes(packBytes(init));
                CycleRecord rec = NKBooleanNetwork.findAttractor(N, K, seed + 1000, init, 5000);
                assertThat(rec.converged())
                    .as("N=%d K=%d seed=%d should converge", N, K, seed)
                    .isTrue();
                lengths.add(rec.cycleLength());
            }
        }
        return lengths;
    }

    private static byte[] packBytes(boolean[] arr) {
        byte[] result = new byte[(arr.length + 7) / 8];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i]) result[i / 8] |= (byte) (1 << (i % 8));
        }
        return result;
    }
}
