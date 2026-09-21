package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 294 — CycleOutputBuffer unit tests. */
class CycleOutputBufferTest {

    @Test
    void emptyBuffer() {
        var b = new CycleOutputBuffer(10);
        assertThat(b.size()).isZero();
    }

    @Test
    void addResult() {
        var b = new CycleOutputBuffer(10);
        var svc = new BrainLoopService();
        b.add(svc.cycle("hello"));
        assertThat(b.size()).isEqualTo(1);
    }

    @Test
    void drainReturnsAll() {
        var b = new CycleOutputBuffer(10);
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) b.add(svc.cycle("x" + i));
        var drained = b.drain();
        assertThat(drained).hasSize(5);
        assertThat(b.size()).isZero();
    }

    @Test
    void evictionAtMax() {
        var b = new CycleOutputBuffer(3);
        var svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) b.add(svc.cycle("x" + i));
        assertThat(b.size()).isEqualTo(3);
    }

    @Test
    void snapshotReturnsCopy() {
        var b = new CycleOutputBuffer(10);
        var svc = new BrainLoopService();
        b.add(svc.cycle("x"));
        var snap = b.snapshot();
        b.add(svc.cycle("y"));
        assertThat(snap).hasSize(1);
    }
}
