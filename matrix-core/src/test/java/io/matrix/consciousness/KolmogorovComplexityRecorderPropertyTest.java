package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W140 — Kolmogorov Complexity Recorder property-based tests.
 */
class KolmogorovComplexityRecorderPropertyTest {

    @Property(tries = 100)
    void propertyCaptureAddsSnapshot(@ForAll("trajectories") long[] traj,
                                     @ForAll("cycleNums") int cycleNum) {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(cycleNum, traj);
        assertThat(r.snapshotCount()).isEqualTo(1);
        assertThat(r.latest().cycleNumber()).isEqualTo(cycleNum);
    }

    @Property(tries = 50)
    void propertyCaptureNullTrajectoryStoresEmpty(@ForAll("cycleNums") int cycleNum) {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(cycleNum, null);
        assertThat(r.latest().trajectoryUsed()).isEmpty();
        assertThat(r.latest().kolmogorovK()).isEqualTo(0.0);
    }

    @Property(tries = 100)
    void propertyCaptureClonesTrajectory(@ForAll("trajectories") long[] traj) {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        long[] original = traj.clone();
        r.capture(0, traj);
        traj[0] = 999L;  // mutate
        // Stored version should be unchanged
        assertThat(r.latest().trajectoryUsed()).containsExactly(original);
    }

    @Property(tries = 30)
    void propertyRingBufferWraparound(@ForAll("intSeeds") int seed) {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder(3);
        java.util.Random rng = new java.util.Random(seed);
        for (int i = 0; i < 5; i++) {
            long[] traj = {rng.nextLong(), rng.nextLong()};
            r.capture(i, traj);
        }
        // Only 3 snapshots stored (ring buffer capacity)
        assertThat(r.snapshotCount()).isEqualTo(3);
    }

    @Property(tries = 50)
    void propertyGetChronologicalOrder(@ForAll("numSnapshots") int n) {
        if (n < 1 || n > 32) return;
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        for (int i = 0; i < n; i++) {
            r.capture(i, new long[]{(long) i});
        }
        for (int i = 0; i < n; i++) {
            assertThat(r.get(i).cycleNumber()).isEqualTo(i);
        }
    }

    @Property(tries = 30)
    void propertyMeanKIsZeroForEmpty() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        assertThat(r.meanK()).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyClearRemovesAll(@ForAll("numSnapshots") int n) {
        if (n < 1) return;
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        for (int i = 0; i < n; i++) r.capture(i, new long[]{1L, 2L});
        r.clear();
        assertThat(r.snapshotCount()).isEqualTo(0);
    }

    @Property(tries = 50)
    void propertyCapacityMatchesConstructor(@ForAll("capacities") int cap) {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder(cap);
        assertThat(r.capacity()).isEqualTo(cap);
    }

    @Provide
    Arbitrary<long[]> trajectories() {
        return Arbitraries.integers().between(1, 16).flatMap(t ->
            Arbitraries.longs().between(0, 1000).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<Integer> cycleNums() {
        return Arbitraries.integers().between(0, 1000);
    }

    @Provide
    Arbitrary<Integer> capacities() {
        return Arbitraries.integers().between(1, 64);
    }

    @Provide
    Arbitrary<Integer> numSnapshots() {
        return Arbitraries.integers().between(0, 32);
    }

    @Provide
    Arbitrary<Integer> intSeeds() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
