package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KolmogorovComplexitySnapshotTest {

    @Test
    void snapshotStoresAllFields() {
        long[] traj = {1L, 2L, 3L, 4L};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(
            5, 10, traj, 32.0, 12345L
        );
        assertThat(s.cycleNumber()).isEqualTo(5);
        assertThat(s.bufferIndex()).isEqualTo(10);
        assertThat(s.trajectoryUsed()).isSameAs(traj);
        assertThat(s.kolmogorovK()).isEqualTo(32.0);
        assertThat(s.snapshotHash()).isEqualTo(12345L);
    }

    @Test
    void negativeCycleNumberThrows() {
        long[] traj = {1L};
        assertThatThrownBy(() -> new KolmogorovComplexitySnapshot(-1, 0, traj, 0.0, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeBufferIndexThrows() {
        long[] traj = {1L};
        assertThatThrownBy(() -> new KolmogorovComplexitySnapshot(0, -1, traj, 0.0, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTrajectoryThrows() {
        assertThatThrownBy(() -> new KolmogorovComplexitySnapshot(0, 0, null, 0.0, 0L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recomputeKReturnsKolmogorovEstimate() {
        long[] traj = {1L, 2L, 3L, 4L, 5L};
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(
            0, 0, traj, 0.0, 0L  // K=0 just placeholder
        );
        double expected = KolmogorovComplexity.estimate(traj);
        assertThat(s.recomputeK()).isEqualTo(expected);
    }

    @Test
    void normalizedKBoundedZeroOne() {
        long[] traj = {1L, 2L, 3L, 4L, 5L};
        double k = KolmogorovComplexity.estimate(traj);
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, traj, k, 0L);
        double nk = s.normalizedK();
        // Should be in [0, 1] — K / (8 * 5) = K / 40
        assertThat(nk).isBetween(0.0, 1.0);
    }

    @Test
    void normalizedKForEmptyIsZero() {
        long[] empty = new long[0];
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, empty, 0.0, 0L);
        assertThat(s.normalizedK()).isEqualTo(0.0);
    }

    @Test
    void normalizedKForHighComplexityApproachesOne() {
        // Long random sequence → high K → ratio close to 1
        long[] random = new long[20];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 20; i++) random[i] = rng.nextLong();
        double k = KolmogorovComplexity.estimate(random);
        KolmogorovComplexitySnapshot s = new KolmogorovComplexitySnapshot(0, 0, random, k, 0L);
        double nk = s.normalizedK();
        assertThat(nk).isGreaterThan(0.3);
    }
}
