package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W132 — NK Boolean network property-based tests.
 *
 * <p>Property-based verification of W108 NKBooleanNetwork.
 */
class NKBooleanNetworkPropertyTest {

    @Property(tries = 20)
    void propertyEdgeOfChaosKBounded(@ForAll("sizes") int N) {
        int k = NKBooleanNetwork.edgeOfChaosK(N);
        assertThat(k).isBetween(0, N - 1);
    }

    @Property(tries = 50)
    void propertyKZeroConvergesImmediately(@ForAll("smallSizes") int N, @ForAll("seeds") long seed) {
        boolean[] initial = new boolean[N];
        new Random(seed).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord rec = NKBooleanNetwork.findAttractor(N, 0, seed, initial, 100);
        assertThat(rec.converged()).isTrue();
        assertThat(rec.cycleLength()).isEqualTo(1);
    }

    @Property(tries = 30)
    void propertyHighKConverges(@ForAll("smallSizes") int N, @ForAll("seeds") long seed) {
        if (N < 3) return;
        boolean[] initial = new boolean[N];
        new Random(seed).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord rec = NKBooleanNetwork.findAttractor(N, N - 1, seed, initial, 5000);
        assertThat(rec.converged()).isTrue();
        assertThat(rec.cycleLength()).isGreaterThanOrEqualTo(1);
    }

    @Property(tries = 50)
    void propertyDeterministicForSameSeed(@ForAll("smallSizes") int N, @ForAll("seeds") long seed) {
        if (N < 3) return;
        boolean[] initial = new boolean[N];
        new Random(seed).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord r1 = NKBooleanNetwork.findAttractor(N, 2, seed, initial, 100);
        NKBooleanNetwork.CycleRecord r2 = NKBooleanNetwork.findAttractor(N, 2, seed, initial, 100);
        assertThat(r1.cycleLength()).isEqualTo(r2.cycleLength());
    }

    @Provide
    Arbitrary<Integer> sizes() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> smallSizes() {
        return Arbitraries.integers().between(3, 12);
    }

    @Provide
    Arbitrary<Long> seeds() {
        return Arbitraries.longs().between(0, Long.MAX_VALUE / 2);
    }

    /** Pack booleans into byte[] for nextBytes. */
    private static byte[] packBytes(boolean[] arr) {
        byte[] result = new byte[(arr.length + 7) / 8];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i]) result[i / 8] |= (byte) (1 << (i % 8));
        }
        return result;
    }
}
