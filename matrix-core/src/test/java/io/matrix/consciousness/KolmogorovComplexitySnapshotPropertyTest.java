package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W141 — Kolmogorov Complexity Snapshot property-based tests.
 */
class KolmogorovComplexitySnapshotPropertyTest {

    @Property(tries = 100)
    void propertySingleStateReturns64(@ForAll("longValues") long value) {
        long[] single = {value};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, single, 64.0, 0L);
        assertThat(s.recomputeK()).isEqualTo(64.0);
    }

    @Property(tries = 100)
    void propertyEmptyTrajectoryKIsZero() {
        long[] empty = new long[0];
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, empty, 0.0, 0L);
        assertThat(s.recomputeK()).isEqualTo(0.0);
        assertThat(s.normalizedK()).isEqualTo(0.0);
    }

    @Property(tries = 100)
    void propertyNormalizedKBounded(@ForAll("trajectories") long[] traj,
                                     @ForAll("kValues") double k) {
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, traj, k, 0L);
        double nk = s.normalizedK();
        assertThat(nk).isGreaterThanOrEqualTo(0.0);
        // K / (8 * length) — can exceed 1.0 if k > max, but shouldn't be negative
    }

    @Property(tries = 100)
    void propertyCycleNumberStored(@ForAll("cycleNums") int cycleNum) {
        long[] traj = {1L, 2L};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(cycleNum, 0, traj, 0.0, 0L);
        assertThat(s.cycleNumber()).isEqualTo(cycleNum);
    }

    @Property(tries = 100)
    void propertyBufferIndexStored(@ForAll("bufferIndices") int idx) {
        long[] traj = {1L};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, idx, traj, 0.0, 0L);
        assertThat(s.bufferIndex()).isEqualTo(idx);
    }

    @Property(tries = 100)
    void propertySnapshotHashStored(@ForAll("hashes") long hash) {
        long[] traj = {1L};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, traj, 0.0, hash);
        assertThat(s.snapshotHash()).isEqualTo(hash);
    }

    @Provide
    Arbitrary<Long> longValues() {
        return Arbitraries.longs().between(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);
    }

    @Provide
    Arbitrary<long[]> trajectories() {
        return Arbitraries.integers().between(0, 32).flatMap(t ->
            Arbitraries.longs().between(0, 1000).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<Double> kValues() {
        return Arbitraries.doubles().between(0.0, 1000.0);
    }

    @Provide
    Arbitrary<Integer> cycleNums() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Integer> bufferIndices() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Long> hashes() {
        return Arbitraries.longs().between(Long.MIN_VALUE, Long.MAX_VALUE);
    }
}
