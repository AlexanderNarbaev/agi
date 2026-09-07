package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 182 — BrainLoopServiceV2 tests. */
class BrainLoopServiceV2Test {

    @Test
    void cycleWithoutImpulses() {
        var v2 = new BrainLoopServiceV2();
        var r = v2.cycle("Hello", List.of());
        assertThat(r.impulseCount()).isZero();
        assertThat(r.focusCount()).isGreaterThan(0);
    }

    @Test
    void cycleWithImpulses() {
        var v2 = new BrainLoopServiceV2();
        List<Impulse> impulses = List.of(
                new Impulse(Impulse.Source.CURIOSITY, 0.8, 0.5, "weather"),
                new Impulse(Impulse.Source.INTEGRITY, 0.5, 0.4, "rules")
        );
        var r = v2.cycle("What's the weather?", impulses);
        assertThat(r.impulseCount()).isEqualTo(2);
        assertThat(r.focusCount()).isGreaterThanOrEqualTo(2); // at least 2 impulses
    }

    @Test
    void adversarialInputDenies() {
        var v2 = new BrainLoopServiceV2();
        var r = v2.cycle("hi\u0001world", List.of());
        assertThat(r.accepted()).isFalse();
        assertThat(r.gateReason()).contains("adversarial");
    }

    @Test
    void cyclesAreDeterministic() {
        var a = new BrainLoopServiceV2();
        var b = new BrainLoopServiceV2();
        var ra = a.cycle("same input", List.of());
        var rb = b.cycle("same input", List.of());
        assertThat(ra.action()).isEqualTo(rb.action());
        assertThat(ra.gateReason()).isEqualTo(rb.gateReason());
    }

    @Test
    void traceCapturesAllSteps() {
        var v2 = new BrainLoopServiceV2();
        v2.cycle("Hello", List.of());
        v2.cycle("World", List.of());
        // 2 cycles × 5 steps = 10
        assertThat(v2.trace().count()).isEqualTo(10);
    }

    @Test
    void cycleResultRecord() {
        var r = new BrainLoopServiceV2().cycle("X", List.of());
        assertThat(r.gateReason()).isNotNull();
    }
}
