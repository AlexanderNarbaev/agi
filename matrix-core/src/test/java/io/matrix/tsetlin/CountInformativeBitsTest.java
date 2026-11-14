package io.matrix.tsetlin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The width contract of {@link TsetlinTrainer#countInformativeBits}: every physical bit of
 * the row counts, not only the first sixty-four.
 *
 * <p>The EVID-BASE independent review (2026-11-14) proved the earlier implementation shifted
 * {@code row[0]} by {@code j}, which Java wraps at 64: for a 256-bit feature it returned 4
 * for one differing bit and 0 for a difference at bit 64. These are the two regressions.</p>
 */
class CountInformativeBitsTest {

    @Test
    @DisplayName("one differing physical bit counts once, not four times")
    void oneBitCountsOnce() {
        long[][] inputs = {
                {0L, 0L, 0L, 0L},
                {1L, 0L, 0L, 0L}};

        assertThat(TsetlinTrainer.countInformativeBits(inputs, 256)).isEqualTo(1);
    }

    @Test
    @DisplayName("a difference at exactly bit 64 — the first bit of the second word — is seen")
    void bitSixtyFourIsSeen() {
        long[][] inputs = {
                {0L, 0L, 0L, 0L},
                {0L, 1L, 0L, 0L}};   // physical bit 64

        assertThat(TsetlinTrainer.countInformativeBits(inputs, 256)).isEqualTo(1);
    }

    @Test
    @DisplayName("a difference beyond the first word is seen")
    void laterWordsAreSeen() {
        long[][] inputs = {
                {0L, 0L, 0L, 0L},
                {0L, 1L << 8, 0L, 0L}};   // physical bit 72

        assertThat(TsetlinTrainer.countInformativeBits(inputs, 256)).isEqualTo(1);
    }

    @Test
    @DisplayName("the fold's own width still behaves as before")
    void narrowWidthsUnchanged() {
        long[][] inputs = {
                {0b0000L},
                {0b0101L}};

        assertThat(TsetlinTrainer.countInformativeBits(inputs, 16)).isEqualTo(2);
    }
}
