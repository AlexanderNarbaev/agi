package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 236 — CyclePrioritizer unit tests. */
class CyclePrioritizerTest {

    @Test
    void emptyQueue() {
        var p = new CyclePrioritizer();
        assertThat(p.size()).isZero();
        assertThat(p.dequeue()).isNull();
    }

    @Test
    void enqueueIncrementsSize() {
        var p = new CyclePrioritizer();
        p.enqueue("a", 1);
        p.enqueue("b", 2);
        assertThat(p.size()).isEqualTo(2);
    }

    @Test
    void dequeueReturnsHighestPriority() {
        var p = new CyclePrioritizer();
        p.enqueue("low", 1);
        p.enqueue("high", 10);
        p.enqueue("mid", 5);
        var first = p.dequeue();
        assertThat(first.input()).isEqualTo("high");
        assertThat(first.priority()).isEqualTo(10);
    }

    @Test
    void dequeueTieBreakerByInsertionOrder() {
        var p = new CyclePrioritizer();
        p.enqueue("first", 5);
        p.enqueue("second", 5);
        var first = p.dequeue();
        var second = p.dequeue();
        // First comes first (FIFO for ties)
        assertThat(first.id()).isLessThan(second.id());
    }

    @Test
    void snapshotInPriorityOrder() {
        var p = new CyclePrioritizer();
        p.enqueue("a", 1);
        p.enqueue("b", 5);
        p.enqueue("c", 3);
        var list = p.snapshot();
        assertThat(list.get(0).input()).isEqualTo("b");
        assertThat(list.get(1).input()).isEqualTo("c");
        assertThat(list.get(2).input()).isEqualTo("a");
    }

    @Test
    void idsAreMonotonicallyIncreasing() {
        var p = new CyclePrioritizer();
        p.enqueue("a", 1);
        p.enqueue("b", 1);
        p.enqueue("c", 1);
        var ids = p.snapshot().stream().map(c -> c.id()).toList();
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i)).isGreaterThan(ids.get(i - 1));
        }
    }
}
