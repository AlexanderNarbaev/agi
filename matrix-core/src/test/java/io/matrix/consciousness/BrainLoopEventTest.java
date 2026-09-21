package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 207 — BrainLoopEvent unit tests. */
class BrainLoopEventTest {

    @Test
    void emptyHasNoEvents() {
        var ev = new BrainLoopEvent();
        assertThat(ev.events()).isEmpty();
        assertThat(ev.acceptedCount()).isZero();
        assertThat(ev.deniedCount()).isZero();
    }

    @Test
    void acceptedEventsCount() {
        var ev = new BrainLoopEvent();
        ev.emitCycleAccepted("Q1", "ok-1");
        ev.emitCycleAccepted("Q2", "ok-2");
        ev.emitCycleAccepted("Q3", "ok-3");
        assertThat(ev.acceptedCount()).isEqualTo(3);
    }

    @Test
    void deniedEventsCount() {
        var ev = new BrainLoopEvent();
        ev.emitCycleDenied("X", "adversarial");
        ev.emitCycleDenied("Y", "too large");
        assertThat(ev.deniedCount()).isEqualTo(2);
    }

    @Test
    void gateTriggeredEmits() {
        var ev = new BrainLoopEvent();
        ev.emitGateTriggered("ethical violation");
        var events = ev.events();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type())
                .isEqualTo(BrainLoopEvent.Type.GATE_TRIGGERED);
    }

    @Test
    void sequenceNumbersIncrement() {
        var ev = new BrainLoopEvent();
        var a = ev.emitCycleAccepted("a", "ok");
        var b = ev.emitCycleAccepted("b", "ok");
        var c = ev.emitCycleDenied("c", "denied");
        assertThat(a.sequenceNumber()).isEqualTo(1);
        assertThat(b.sequenceNumber()).isEqualTo(2);
        assertThat(c.sequenceNumber()).isEqualTo(3);
    }

    @Test
    void eventTypeEnum() {
        assertThat(BrainLoopEvent.Type.values()).hasSize(5);
        assertThat(BrainLoopEvent.Type.valueOf("CYCLE_ACCEPTED")).isNotNull();
        assertThat(BrainLoopEvent.Type.valueOf("GATE_TRIGGERED")).isNotNull();
    }

    @Test
    void clearResets() {
        var ev = new BrainLoopEvent();
        ev.emitCycleAccepted("a", "ok");
        ev.emitCycleAccepted("b", "ok");
        ev.clear();
        assertThat(ev.events()).isEmpty();
        // Sequence resets
        var e = ev.emitCycleAccepted("c", "ok");
        assertThat(e.sequenceNumber()).isEqualTo(1);
    }

    @Test
    void traceReceivedEvent() {
        var ev = new BrainLoopEvent();
        var e = ev.emitTraceReceived(50);
        assertThat(e.output()).isEqualTo("count=50");
        assertThat(e.type())
                .isEqualTo(BrainLoopEvent.Type.TRACE_RECEIVED);
    }
}
