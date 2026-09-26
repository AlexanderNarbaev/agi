package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LandauerBoundTest {

    @Test
    void room_temperature_per_bit_is_correct() {
        // kT ln(2) at 300K ≈ 2.87e-21 J
        double e = LandauerBound.perBitJoules(LandauerBound.T_ROOM);
        assertThat(e).isCloseTo(2.87e-21,
            org.assertj.core.data.Offset.offset(0.05e-21));
    }

    @Test
    void zero_bits_is_zero_energy() {
        assertThat(LandauerBound.totalJoulesRoom(0)).isEqualTo(0.0);
    }

    @Test
    void one_bit_matches_per_bit() {
        assertThat(LandauerBound.totalJoulesRoom(1))
            .isCloseTo(LandauerBound.E_MIN_ROOM,
                org.assertj.core.data.Offset.offset(1e-30));
    }

    @Test
    void total_scales_linearly() {
        long n = 1_000_000L;
        double e100 = LandauerBound.totalJoulesRoom(n);
        double e200 = LandauerBound.totalJoulesRoom(2 * n);
        assertThat(e200).isCloseTo(2 * e100, org.assertj.core.data.Offset.offset(1e-30));
    }

    @Test
    void colder_temperature_is_more_efficient() {
        double room = LandauerBound.perBitJoules(300.0);
        double cold = LandauerBound.perBitJoules(4.0);  // liquid helium
        assertThat(cold).isLessThan(room);
    }

    @Test
    void invalid_temperature_rejected() {
        assertThat(catchThrowable(() -> LandauerBound.perBitJoules(0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> LandauerBound.perBitJoules(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalid_bits_rejected() {
        assertThat(catchThrowable(() -> LandauerBound.totalJoules(300, -1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void max_forget_rate_scales_with_watts() {
        double rate1w = LandauerBound.maxForgetRateBitsPerSec(300, 1.0);
        double rate2w = LandauerBound.maxForgetRateBitsPerSec(300, 2.0);
        assertThat(rate2w).isCloseTo(2 * rate1w,
            org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    void watts_must_be_positive() {
        assertThat(catchThrowable(() -> LandauerBound.maxForgetRateBitsPerSec(300, 0)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
