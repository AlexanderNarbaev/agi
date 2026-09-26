package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActionAcceptorTest {

    @Test
    void exact_match_returns_MATCH() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        ActionAcceptor.Verdict v = acc.accept("hdc", 0.9, 0.9);
        assertThat(v).isEqualTo(ActionAcceptor.Verdict.MATCH);
        assertThat(acc.totalMatches()).isEqualTo(1);
    }

    @Test
    void small_mismatch_returns_MISMATCH_SMALL() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        ActionAcceptor.Verdict v = acc.accept("hdc", 0.9, 0.85);
        assertThat(v).isEqualTo(ActionAcceptor.Verdict.MISMATCH_SMALL);
    }

    @Test
    void large_mismatch_returns_MISMATCH_LARGE() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        ActionAcceptor.Verdict v = acc.accept("hdc", 0.9, 0.0);
        assertThat(v).isEqualTo(ActionAcceptor.Verdict.MISMATCH_LARGE);
        assertThat(acc.totalLargeMismatches()).isEqualTo(1);
    }

    @Test
    void attribution_is_per_stage() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        acc.accept("hdc", 0.9, 0.9);
        acc.accept("hdc", 0.9, 0.0);
        acc.accept("bir", 0.5, 0.5);
        assertThat(acc.attribution().get("hdc")).isEqualTo(2);
        assertThat(acc.attribution().get("bir")).isEqualTo(1);
    }

    @Test
    void mse_is_average_of_squared_errors() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        acc.accept("hdc", 1.0, 1.0);  // err 0
        acc.accept("hdc", 1.0, 0.0);  // err 1
        // MSE = (0 + 1) / 2 = 0.5
        assertThat(acc.meanSquaredError()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void reset_clears_state() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        acc.accept("hdc", 0.9, 0.0);
        acc.reset();
        assertThat(acc.totalPredictions()).isEqualTo(0);
        assertThat(acc.attribution()).isEmpty();
    }

    @Test
    void null_stage_rejected() {
        ActionAcceptor acc = new ActionAcceptor(0.5);
        assertThat(catchThrowable(() -> acc.accept(null, 0.5, 0.5)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(catchThrowable(() -> acc.accept("", 0.5, 0.5)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowable(Runnable r) {
        try { r.run(); return null; } catch (Throwable t) { return t; }
    }
}
