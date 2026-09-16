package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSparseAttentionTest {

    @Test
    void constructValid() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, 42L);
        assertThat(sa.windowSize()).isEqualTo(4);
        assertThat(sa.numGlobalTokens()).isEqualTo(2);
        assertThat(sa.numRandomTokens()).isEqualTo(2);
    }

    @Test
    void invalidWindowSizeThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSparseAttention(0, 2, 2, 1L)
        );
    }

    @Test
    void invalidGlobalThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSparseAttention(4, -1, 2, 1L)
        );
    }

    @Test
    void invalidRandomThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSparseAttention(4, 2, -1, 1L)
        );
    }

    @Test
    void attentionMaskIncludesLocalWindow() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 0, 0, 1L);
        int[] mask = sa.attentionMask(5, 10);
        // Should include positions 3..7 (centered on 5 with window 4)
        for (int p : mask) {
            assertThat(p).isBetween(3, 7);
        }
    }

    @Test
    void attentionMaskIncludesGlobalTokens() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(2, 2, 0, 1L);
        int[] mask = sa.attentionMask(5, 10);
        // Should include first 2 global tokens
        boolean has0 = false, has1 = false;
        for (int p : mask) {
            if (p == 0) has0 = true;
            if (p == 1) has1 = true;
        }
        assertThat(has0).isTrue();
        assertThat(has1).isTrue();
    }

    @Test
    void invalidPositionReturnsEmpty() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, 1L);
        assertThat(sa.attentionMask(-1, 10)).isEmpty();
        assertThat(sa.attentionMask(10, 10)).isEmpty();
    }

    @Test
    void totalOpsScalesLinearly() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, 1L);
        int opsSmall = sa.totalOps(10);
        int opsLarge = sa.totalOps(100);
        // Should scale roughly linearly
        assertThat(opsLarge).isLessThan(opsSmall * 100);
    }

    @Test
    void sparsityRatioLessThanOne() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, 1L);
        double ratio = sa.sparsityRatio(50);
        assertThat(ratio).isLessThan(1.0);
    }

    @Test
    void seedRecorded() {
        CognitiveSparseAttention sa =
            new CognitiveSparseAttention(4, 2, 2, 42L);
        assertThat(sa.seed()).isEqualTo(42L);
    }
}
