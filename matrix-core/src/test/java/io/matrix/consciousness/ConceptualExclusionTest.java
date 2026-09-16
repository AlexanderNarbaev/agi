package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

class ConceptualExclusionTest {

    @Test
    void jaccardExclusionIsZeroForIdenticalSets() {
        long[] a = {1L, 2L, 3L};
        long[] b = {1L, 2L, 3L};
        assertThat(ConceptualExclusion.jaccardExclusion(a, b)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void jaccardExclusionIsOneForDisjointSets() {
        long[] a = {1L, 2L, 3L};
        long[] b = {4L, 5L, 6L};
        assertThat(ConceptualExclusion.jaccardExclusion(a, b)).isEqualTo(1.0);
    }

    @Test
    void jaccardExclusionPartialOverlap() {
        long[] a = {1L, 2L, 3L};
        long[] b = {2L, 3L, 4L};
        // intersection = {2,3}, union = {1,2,3,4}, jaccard = 0.5, exclusion = 0.5
        assertThat(ConceptualExclusion.jaccardExclusion(a, b)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void bitMaskExclusionIdenticalIsZero() {
        long[] a = {0xFL, 0xAL, 0x5L};
        long[] b = {0xFL, 0xAL, 0x5L};
        assertThat(ConceptualExclusion.bitMaskExclusion(a, b)).isEqualTo(0.0);
    }

    @Test
    void bitMaskExclusionComplementaryIsOne() {
        long[] a = {0x0L, 0x0L, 0x0L};
        long[] b = {-1L, -1L, -1L};
        assertThat(ConceptualExclusion.bitMaskExclusion(a, b)).isEqualTo(1.0);
    }

    @Test
    void distributionalExclusionIdenticalConstants() {
        long[] a = {5L, 5L, 5L, 5L};
        long[] b = {5L, 5L, 5L, 5L};
        assertThat(ConceptualExclusion.distributionalExclusion(a, b)).isEqualTo(0.0);
    }

    @Test
    void distributionalExclusionSeparatedDistributions() {
        long[] a = {0L, 1L, 2L, 3L}; // mean 1.5, var 1.25
        long[] b = {100L, 101L, 102L, 103L}; // mean 101.5, var 1.25
        double excl = ConceptualExclusion.distributionalExclusion(a, b);
        // 100-unit separation vs ~2*sigma*2 ≈ 4 → high exclusion
        assertThat(excl).isGreaterThan(0.9);
    }

    @Test
    void compositeExclusionHigherForMoreDifferent() {
        long[] a = {1L, 2L, 3L, 4L, 5L};
        long[] b = {1L, 2L, 3L, 4L, 5L};
        long[] c = {100L, 200L, 300L, 400L, 500L};
        double exclAB = ConceptualExclusion.compositeExclusion(a, b);
        double exclAC = ConceptualExclusion.compositeExclusion(a, c);
        assertThat(exclAC).isGreaterThan(exclAB);
    }

    @Test
    void compositeExclusionIsInZeroOne() {
        Random rng = new Random(42);
        long[] a = new long[16];
        long[] b = new long[16];
        for (int i = 0; i < 16; i++) {
            a[i] = rng.nextLong();
            b[i] = rng.nextLong();
        }
        double excl = ConceptualExclusion.compositeExclusion(a, b);
        assertThat(excl).isBetween(0.0, 1.0);
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
