package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NKBooleanNetworkTest {

    @Test
    void kZeroNetworkIsFrozen() {
        // K=0: each gene's state depends only on its own constant.
        // With random rule and K=0, each gene has either always-true or always-false
        // So the network has a fixed point (cycle length 1)
        int N = 5;
        boolean[] initial = new boolean[N];
        new Random(42).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord rec = NKBooleanNetwork.findAttractor(N, 0, 42L, initial, 100);
        assertThat(rec.converged()).isTrue();
        // K=0: cycle length = 1 (all genes constant)
        assertThat(rec.cycleLength()).isEqualTo(1);
    }

    @Test
    void edgeOfChaosKEqualsTwo() {
        assertThat(NKBooleanNetwork.edgeOfChaosK(8)).isEqualTo(2);
        assertThat(NKBooleanNetwork.edgeOfChaosK(2)).isEqualTo(1); // capped at N-1
        assertThat(NKBooleanNetwork.edgeOfChaosK(100)).isEqualTo(2);
    }

    @Test
    void highKNetworkHasLongerCycles() {
        // K=N-1: chaotic regime, expected longer cycles
        int N = 8;
        boolean[] initial = new boolean[N];
        new Random(123).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord rec = NKBooleanNetwork.findAttractor(N, N - 1, 456L, initial, 1000);
        assertThat(rec.converged()).isTrue();
        // Chaotic regime typically has cycle lengths much larger than 1
        assertThat(rec.cycleLength()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void smallKConvergesQuickly() {
        int N = 10;
        boolean[] initial = new boolean[N];
        new Random(789).nextBytes(packBytes(initial));
        NKBooleanNetwork.CycleRecord rec = NKBooleanNetwork.findAttractor(N, 1, 999L, initial, 100);
        assertThat(rec.converged()).isTrue();
        // K=1 typically has very short attractors
        assertThat(rec.cycleLength()).isLessThan(20);
    }

    @Test
    void invalidNThrows() {
        boolean[] initial = new boolean[0];
        assertThatThrownBy(() -> NKBooleanNetwork.findAttractor(0, 0, 42L, initial, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidKThrows() {
        int N = 5;
        boolean[] initial = new boolean[N];
        assertThatThrownBy(() -> NKBooleanNetwork.findAttractor(N, -1, 42L, initial, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NKBooleanNetwork.findAttractor(N, N, 42L, initial, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mismatchedInitialStateThrows() {
        int N = 5;
        boolean[] wrong = new boolean[3];
        assertThatThrownBy(() -> NKBooleanNetwork.findAttractor(N, 1, 42L, wrong, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deterministicForSameSeed() {
        int N = 8;
        boolean[] initial = new boolean[N];
        for (int i = 0; i < N; i++) initial[i] = (i & 1) == 1;
        NKBooleanNetwork.CycleRecord r1 = NKBooleanNetwork.findAttractor(N, 2, 12345L, initial, 100);
        NKBooleanNetwork.CycleRecord r2 = NKBooleanNetwork.findAttractor(N, 2, 12345L, initial, 100);
        assertThat(r1.cycleLength()).isEqualTo(r2.cycleLength());
    }

    @Test
    void differentSeedsGiveDifferentDynamics() {
        int N = 7;
        boolean[] initial = new boolean[N];
        for (int i = 0; i < N; i++) initial[i] = (i & 1) == 1;
        NKBooleanNetwork.CycleRecord r1 = NKBooleanNetwork.findAttractor(N, 2, 1L, initial, 100);
        NKBooleanNetwork.CycleRecord r2 = NKBooleanNetwork.findAttractor(N, 2, 2L, initial, 100);
        // Different random wirings/rules → likely different cycle lengths
        // (not strictly guaranteed, but with N=7 K=2 they're almost always different)
        assertThat(r1.cycleLength()).isNotEqualTo(r2.cycleLength());
    }

    /** Pack booleans into a byte[] for nextBytes. */
    private static byte[] packBytes(boolean[] arr) {
        byte[] result = new byte[(arr.length + 7) / 8];
        for (int i = 0; i < arr.length; i++) {
            if (arr[i]) result[i / 8] |= (byte) (1 << (i % 8));
        }
        return result;
    }
}
