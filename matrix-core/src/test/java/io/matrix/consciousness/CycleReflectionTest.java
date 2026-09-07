package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 267 — CycleReflection unit tests. */
class CycleReflectionTest {

    @Test
    void reflectOnAllAccepted() {
        var svc = new BrainLoopService();
        var fb = new CycleFeedback();
        fb.record(1, 1.0, "ok");
        var r = CycleReflection.reflect(svc, fb, 0);
        assertThat(r.totalCycles()).isZero();
        assertThat(r.summary()).isEqualTo("no cycles");
    }

    @Test
    void reflectWithDenied() {
        var svc = new BrainLoopService();
        var fb = new CycleFeedback();
        fb.record(1, 0.5, "ok");
        var r = CycleReflection.reflect(svc, fb, 3);
        assertThat(r.deniedCycles()).isEqualTo(3);
        assertThat(r.summary()).isEqualTo("no cycles");
    }

    @Test
    void formatProducesReadableString() {
        var r = new CycleReflection.Reflection(50, 5, 0.5, 0.8, "45/50 accepted");
        String text = CycleReflection.format(r);
        assertThat(text).contains("Reflection");
        assertThat(text).contains("cycles=50");
    }

    @Test
    void reflectionRecordFields() {
        var r = new CycleReflection.Reflection(100, 10, 0.3, 0.7, "test");
        assertThat(r.totalCycles()).isEqualTo(100);
        assertThat(r.deniedCycles()).isEqualTo(10);
        assertThat(r.feedbackTrend()).isEqualTo(0.7);
    }
}
