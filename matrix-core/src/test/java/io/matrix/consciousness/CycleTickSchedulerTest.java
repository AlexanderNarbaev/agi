package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 232 — CycleTickScheduler unit tests. */
class CycleTickSchedulerTest {

    @Test
    void emptyHasZeroTicks() {
        var s = new CycleTickScheduler();
        assertThat(s.tickCount()).isZero();
        assertThat(s.maxPerTick()).isEqualTo(10);
    }

    @Test
    void beginTickIncrements() {
        var s = new CycleTickScheduler();
        s.beginTick();
        s.beginTick();
        s.beginTick();
        assertThat(s.tickCount()).isEqualTo(3);
    }

    @Test
    void dispatchCallsBrainLoop() {
        var svc = new BrainLoopService();
        var s = new CycleTickScheduler();
        var t = s.beginTick();
        var r = s.dispatch(svc, t, "hello");
        assertThat(r).isNotNull();
        assertThat(r.action()).startsWith("ok:");
        assertThat(t.inputs()).contains("hello");
    }

    @Test
    void dispatchRespectsMaxPerTick() {
        var svc = new BrainLoopService();
        var s = new CycleTickScheduler(2);
        var t = s.beginTick();
        s.dispatch(svc, t, "X");
        s.dispatch(svc, t, "Y");
        // 3rd should be rejected
        var r = s.dispatch(svc, t, "Z");
        assertThat(r).isNull();
        assertThat(t.inputs()).hasSize(2);
    }

    @Test
    void customMaxPerTick() {
        var s = new CycleTickScheduler(5);
        assertThat(s.maxPerTick()).isEqualTo(5);
    }

    @Test
    void zeroMaxNormalizedToOne() {
        var s = new CycleTickScheduler(0);
        assertThat(s.maxPerTick()).isEqualTo(1);
    }
}
