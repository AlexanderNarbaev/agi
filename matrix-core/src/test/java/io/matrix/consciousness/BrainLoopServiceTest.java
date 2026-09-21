package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 158 — BrainLoopService unit tests. */
class BrainLoopServiceTest {

    @Test
    void cycleProducesResult() {
        BrainLoopService svc = new BrainLoopService();
        var r = svc.cycle("Hello");
        assertThat(r).isNotNull();
        assertThat(r.action()).startsWith("ok:");
    }

    @Test
    void cleanInputPassesGate() {
        BrainLoopService svc = new BrainLoopService();
        var r = svc.cycle("What is the meaning of life?");
        assertThat(r.accepted()).isTrue();
    }

    @Test
    void adversarialInputFailsGate() {
        BrainLoopService svc = new BrainLoopService();
        var r = svc.cycle("hello\u0001world");
        assertThat(r.accepted()).isFalse();
        assertThat(r.action()).isEqualTo("denied");
    }

    @Test
    void cycleRecordsTrace() {
        BrainLoopService svc = new BrainLoopService();
        svc.cycle("First");
        svc.cycle("Second");
        // Each cycle records 5 trace steps: perception, saliency,
        // attention, gate, action
        assertThat(svc.trace().count()).isEqualTo(10);
    }

    @Test
    void cyclesAreDeterministic() {
        BrainLoopService a = new BrainLoopService();
        BrainLoopService b = new BrainLoopService();
        var ra = a.cycle("Same input");
        var rb = b.cycle("Same input");
        assertThat(ra.action()).isEqualTo(rb.action());
        assertThat(ra.focusCount()).isEqualTo(rb.focusCount());
    }

    @Test
    void hundredCyclesChained() {
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 100; i++) {
            svc.cycle("step " + i);
        }
        // 100 cycles × 5 trace = 500 steps
        assertThat(svc.trace().count()).isEqualTo(500);
    }

    @Test
    void arousalRisesOverTime() {
        BrainLoopService svc = new BrainLoopService();
        double initial = svc.arousal();
        for (int i = 0; i < 50; i++) svc.cycle("test");
        double after = svc.arousal();
        // Should rise above baseline (0.3) due to prediction errors
        assertThat(after).isGreaterThan(initial);
    }

    @Test
    void nullInputHandled() {
        BrainLoopService svc = new BrainLoopService();
        var r = svc.cycle(null);
        // null gets passed to encoder; expected to not crash
        assertThat(r).isNotNull();
    }

    @Test
    void traceIsAccessible() {
        BrainLoopService svc = new BrainLoopService();
        assertThat(svc.trace()).isNotNull();
        assertThat(svc.trace().count()).isZero();
    }

    @Test
    void cycleResultRecord() {
        BrainLoopService svc = new BrainLoopService();
        var r = svc.cycle("X");
        assertThat(r.accepted()).isIn(true, false);
        assertThat(r.action()).isNotNull();
        assertThat(r.arousal()).isBetween(0.0, 1.0);
        assertThat(r.focusCount()).isGreaterThan(0);
    }
}
