package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KolmogorovComplexityRecorderTest {

    @Test
    void defaultCapacityIs32() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        assertThat(r.capacity()).isEqualTo(32);
        assertThat(r.snapshotCount()).isEqualTo(0);
    }

    @Test
    void invalidCapacityThrows() {
        assertThatThrownBy(() -> new KolmogorovComplexityRecorder(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void captureAddsSnapshot() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        long[] traj = {1L, 2L, 3L, 4L};
        r.capture(0, traj);
        assertThat(r.snapshotCount()).isEqualTo(1);
        assertThat(r.latest().trajectoryUsed()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void captureWithNullTrajectoryStoresEmpty() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(0, null);
        assertThat(r.latest().trajectoryUsed()).isEmpty();
        assertThat(r.latest().kolmogorovK()).isEqualTo(0.0);
    }

    @Test
    void captureClonesTrajectory() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        long[] traj = {1L, 2L, 3L};
        r.capture(0, traj);
        traj[0] = 99L;  // Mutate original
        // Snapshot should have stored the original
        assertThat(r.latest().trajectoryUsed()[0]).isEqualTo(1L);
    }

    @Test
    void getRetrievesChronologicalOrder() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(0, new long[]{1L});
        r.capture(1, new long[]{2L});
        r.capture(2, new long[]{3L});
        assertThat(r.get(0).trajectoryUsed()[0]).isEqualTo(1L);
        assertThat(r.get(1).trajectoryUsed()[0]).isEqualTo(2L);
        assertThat(r.get(2).trajectoryUsed()[0]).isEqualTo(3L);
    }

    @Test
    void getOutOfBoundsThrows() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(0, new long[]{1L});
        assertThatThrownBy(() -> r.get(5)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> r.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void ringBufferWrapsAround() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder(3);
        r.capture(0, new long[]{10L});
        r.capture(1, new long[]{20L});
        r.capture(2, new long[]{30L});
        r.capture(3, new long[]{40L});  // overwrites 0
        r.capture(4, new long[]{50L});  // overwrites 1
        assertThat(r.snapshotCount()).isEqualTo(3);
        // Chronological: [30L, 40L, 50L] (oldest to newest)
        assertThat(r.get(0).trajectoryUsed()[0]).isEqualTo(30L);
        assertThat(r.get(1).trajectoryUsed()[0]).isEqualTo(40L);
        assertThat(r.get(2).trajectoryUsed()[0]).isEqualTo(50L);
    }

    @Test
    void allSnapshotsReturnsList() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        for (int i = 0; i < 5; i++) r.capture(i, new long[]{(long) i});
        assertThat(r.allSnapshots()).hasSize(5);
    }

    @Test
    void latestReturnsNullIfEmpty() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        assertThat(r.latest()).isNull();
    }

    @Test
    void meanKIsZeroForEmpty() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        assertThat(r.meanK()).isEqualTo(0.0);
    }

    @Test
    void meanKComputesCorrectly() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(0, new long[]{1L, 2L, 3L, 4L});
        r.capture(1, new long[]{5L, 6L, 7L, 8L});
        double k1 = r.get(0).kolmogorovK();
        double k2 = r.get(1).kolmogorovK();
        assertThat(r.meanK()).isCloseTo((k1 + k2) / 2.0, within(1e-9));
    }

    @Test
    void clearRemovesAllSnapshots() {
        KolmogorovComplexityRecorder r = new KolmogorovComplexityRecorder();
        r.capture(0, new long[]{1L});
        r.capture(1, new long[]{2L});
        r.clear();
        assertThat(r.snapshotCount()).isEqualTo(0);
        assertThat(r.latest()).isNull();
    }

    @Test

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
