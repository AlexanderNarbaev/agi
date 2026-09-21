package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 310 — CycleEvent unit tests. */
class CycleEventTest {

    @Test
    void startEvent() {
        var e = CycleEvent.start(1, "hello");
        assertThat(e.cycleId()).isEqualTo(1);
        assertThat(e.type()).isEqualTo(CycleEvent.EventType.START);
        assertThat(e.input()).isEqualTo("hello");
        assertThat(e.accepted()).isFalse();
    }

    @Test
    void endEvent() {
        var e = CycleEvent.end(1, "hello", "ok", true, 0.5);
        assertThat(e.type()).isEqualTo(CycleEvent.EventType.END);
        assertThat(e.accepted()).isTrue();
        assertThat(e.arousal()).isEqualTo(0.5);
    }

    @Test
    void errorEvent() {
        var e = CycleEvent.error(1, "hello", "denied");
        assertThat(e.type()).isEqualTo(CycleEvent.EventType.ERROR);
        assertThat(e.output()).isEqualTo("denied");
    }

    @Test
    void eventTypeEnum() {
        assertThat(CycleEvent.EventType.values()).hasSize(4);
    }

    @Test
    void timestampIsPositive() {
        var e = CycleEvent.start(1, "x");
        assertThat(e.timestampMillis()).isGreaterThan(0);
    }
}
