package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalogicalConsistencyTest {

    @Test
    void identicalTrajectoriesHaveBitSimilarityOne() {
        long[] a = {1L, 2L, 3L, 4L};
        long[] b = {1L, 2L, 3L, 4L};
        assertThat(AnalogicalConsistency.bitSimilarity(a, b)).isEqualTo(1.0);
    }

    @Test
    void completelyDifferentTrajectoriesHaveLowBitSimilarity() {
        long[] a = new long[8];
        long[] b = new long[8];
        for (int i = 0; i < 8; i++) {
            a[i] = 0L;
            b[i] = -1L; // all bits set
        }
        assertThat(AnalogicalConsistency.bitSimilarity(a, b)).isEqualTo(0.0);
    }

    @Test
    void emptyTrajectoriesReturnOne() {
        assertThat(AnalogicalConsistency.bitSimilarity(new long[0], new long[0])).isEqualTo(1.0);
    }

    @Test
    void mismatchedLengthsThrow() {
        assertThatThrownBy(() -> AnalogicalConsistency.bitSimilarity(new long[3], new long[4]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void structuralSimilarityIsOneForSameOrder() {
        long[] a = {1L, 5L, 3L, 7L, 2L};
        long[] b = {10L, 50L, 30L, 70L, 20L}; // same rank order
        assertThat(AnalogicalConsistency.structuralSimilarity(a, b)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void structuralSimilarityIsLowForInverseOrder() {
        long[] a = {1L, 2L, 3L, 4L, 5L};
        long[] b = {5L, 4L, 3L, 2L, 1L};
        double sim = AnalogicalConsistency.structuralSimilarity(a, b);
        assertThat(sim).isLessThan(0.1); // perfect inverse → 0
    }

    @Test
    void compressionAnalogyIdenticalTrajectoriesCompressWell() {
        long[] a = {1L, 2L, 3L, 4L, 5L};
        long[] b = {1L, 2L, 3L, 4L, 5L};
        double ratio = AnalogicalConsistency.compressionAnalogy(a, b);
        // Identical sequences share full structure
        assertThat(ratio).isLessThan(1.0);
    }

    @Test
    void compressionAnalogyRandomTrajectoriesNearOne() {
        Random rng = new Random(42);
        long[] a = new long[16];
        long[] b = new long[16];
        for (int i = 0; i < 16; i++) {
            a[i] = rng.nextLong();
            b[i] = rng.nextLong();
        }
        double ratio = AnalogicalConsistency.compressionAnalogy(a, b);
        // Random independent sequences should not compress better than separate
        assertThat(ratio).isBetween(0.7, 1.3);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
