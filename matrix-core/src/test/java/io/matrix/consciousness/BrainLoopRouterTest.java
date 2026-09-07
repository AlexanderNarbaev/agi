package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 247 — BrainLoopRouter unit tests. */
class BrainLoopRouterTest {

    @Test
    void allPhasesEnabledByDefault() {
        var r = new BrainLoopRouter();
        for (BrainLoopRouter.Phase p : BrainLoopRouter.Phase.values()) {
            assertThat(r.isEnabled(p)).isTrue();
        }
    }

    @Test
    void setEnabledDisablesPhase() {
        var r = new BrainLoopRouter();
        r.setEnabled(BrainLoopRouter.Phase.GATE, false);
        assertThat(r.isEnabled(BrainLoopRouter.Phase.GATE)).isFalse();
        assertThat(r.isEnabled(BrainLoopRouter.Phase.ACTION)).isTrue();
    }

    @Test
    void shouldProcessMatchesEnabled() {
        var r = new BrainLoopRouter();
        r.setEnabled(BrainLoopRouter.Phase.TRACE, false);
        assertThat(r.shouldProcess(BrainLoopRouter.Phase.TRACE)).isFalse();
        assertThat(r.shouldProcess(BrainLoopRouter.Phase.GATE)).isTrue();
    }

    @Test
    void activePhasesReturnsEnabled() {
        var r = new BrainLoopRouter();
        r.setEnabled(BrainLoopRouter.Phase.SALIENCY, false);
        r.setEnabled(BrainLoopRouter.Phase.TRACE, false);
        var active = r.activePhases();
        assertThat(active).doesNotContain(
                BrainLoopRouter.Phase.SALIENCY,
                BrainLoopRouter.Phase.TRACE);
        assertThat(active).contains(
                BrainLoopRouter.Phase.PERCEPTION,
                BrainLoopRouter.Phase.GATE);
    }

    @Test
    void phaseEnum() {
        assertThat(BrainLoopRouter.Phase.values()).hasSize(7);
    }
}
