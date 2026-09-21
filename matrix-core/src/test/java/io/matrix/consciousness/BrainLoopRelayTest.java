package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 248 — BrainLoopRelay unit tests. */
class BrainLoopRelayTest {

    @Test
    void emptyRelay() {
        var r = new BrainLoopRelay(10);
        assertThat(r.size()).isZero();
        assertThat(r.take()).isNull();
    }

    @Test
    void offerAndTake() {
        var r = new BrainLoopRelay(10);
        assertThat(r.offer("test", "hello")).isTrue();
        BrainLoopRelay.Offer o = r.take();
        assertThat(o).isNotNull();
        assertThat(o.input()).isEqualTo("hello");
        assertThat(o.source()).isEqualTo("test");
    }

    @Test
    void fifoOrder() {
        var r = new BrainLoopRelay(10);
        r.offer("a", "1");
        r.offer("b", "2");
        r.offer("c", "3");
        assertThat(r.take().input()).isEqualTo("1");
        assertThat(r.take().input()).isEqualTo("2");
        assertThat(r.take().input()).isEqualTo("3");
    }

    @Test
    void countersTrack() {
        var r = new BrainLoopRelay(10);
        r.offer("a", "1");
        r.offer("b", "2");
        r.take();
        assertThat(r.totalOffered()).isEqualTo(2);
        assertThat(r.totalTaken()).isEqualTo(1);
    }

    @Test
    void zeroCapacityNormalized() {
        var r = new BrainLoopRelay(0);
        assertThat(r.capacity()).isEqualTo(1);
    }
}
