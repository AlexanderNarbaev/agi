package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 260 — BrainLoopReplay unit tests. */
class BrainLoopReplayTest {

    @Test
    void emptyReplay() {
        var r = new BrainLoopReplay();
        assertThat(r.size()).isZero();
    }

    @Test
    void recordAddsEntry() {
        var r = new BrainLoopReplay();
        r.record("X", true, "ok: X");
        assertThat(r.size()).isEqualTo(1);
    }

    @Test
    void sequencesIncrement() {
        var r = new BrainLoopReplay();
        var a = r.record("A", true, "ok: A");
        var b = r.record("B", true, "ok: B");
        assertThat(a.sequence()).isEqualTo(1);
        assertThat(b.sequence()).isEqualTo(2);
    }

    @Test
    void replayAgainstBrain() {
        var r = new BrainLoopReplay();
        r.record("hello", true, "");
        r.record("world", true, "");
        var svc = new BrainLoopService();
        int accepted = r.replay(svc);
        assertThat(accepted).isEqualTo(2);
    }

    @Test
    void clearResets() {
        var r = new BrainLoopReplay();
        r.record("X", true, "");
        r.clear();
        assertThat(r.size()).isZero();
    }

    @Test
    void replayEntryRecordFields() {
        var r = new BrainLoopReplay();
        var e = r.record("hello", false, "denied");
        assertThat(e.input()).isEqualTo("hello");
        assertThat(e.accepted()).isFalse();
        assertThat(e.action()).isEqualTo("denied");
    }
}
