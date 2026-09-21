package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 209 — BrainLoopHealth unit tests. */
class BrainLoopHealthTest {

    @Test
    void freshServiceHealth() {
        var svc = new BrainLoopService();
        var h = BrainLoopHealth.compute(svc);
        // Baseline arousal=0.3 → score should be in [0, 1]
        assertThat(h.score()).isBetween(0.0, 1.0);
        assertThat(h.status()).isIn("healthy", "stable", "degraded");
    }

    @Test
    void healthScoreFormat() {
        var svc = new BrainLoopService();
        var h = BrainLoopHealth.compute(svc);
        String text = BrainLoopHealth.format(h);
        assertThat(text).contains("Health");
        assertThat(text).contains("score=");
        assertThat(text).contains("status=");
    }

    @Test
    void healthAfterCycles() {
        var svc = new BrainLoopService();
        for (int i = 0; i < 100; i++) svc.cycle("Q-" + i);
        var h = BrainLoopHealth.compute(svc);
        // After many cycles, arousal may have changed
        assertThat(h.score()).isBetween(0.0, 1.0);
    }

    @Test
    void healthScoreRecord() {
        var h = new BrainLoopHealth.HealthScore(0.85, "healthy", 0.5, 0.9);
        assertThat(h.score()).isEqualTo(0.85);
        assertThat(h.status()).isEqualTo("healthy");
        assertThat(h.arousal()).isEqualTo(0.5);
    }

    @Test
    void statusBuckets() {
        assertThat(BrainLoopHealth.compute(new BrainLoopService()).status())
                .isIn("healthy", "stable", "degraded");
    }
}
