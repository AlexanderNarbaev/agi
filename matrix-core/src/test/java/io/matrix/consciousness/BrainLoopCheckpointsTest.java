package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 242 — BrainLoopCheckpoints unit tests. */
class BrainLoopCheckpointsTest {

    @Test
    void emptyCheckpoints() {
        var c = new BrainLoopCheckpoints();
        assertThat(c.size()).isZero();
        assertThat(c.list()).isEmpty();
    }

    @Test
    void createCheckpoint() {
        var svc = new BrainLoopService();
        var c = new BrainLoopCheckpoints();
        var cp = c.create(svc, "start");
        assertThat(cp.tag()).isEqualTo("start");
        assertThat(cp.traceCount()).isZero();
        assertThat(c.size()).isEqualTo(1);
    }

    @Test
    void findByTagReturnsCheckpoint() {
        var svc = new BrainLoopService();
        var c = new BrainLoopCheckpoints();
        c.create(svc, "init");
        c.create(svc, "post-cycle");
        var found = c.findByTag("init");
        assertThat(found).isNotNull();
        assertThat(found.tag()).isEqualTo("init");
    }

    @Test
    void findByTagMissingReturnsNull() {
        var c = new BrainLoopCheckpoints();
        assertThat(c.findByTag("missing")).isNull();
    }

    @Test
    void checkpointsTrackActivity() {
        var svc = new BrainLoopService();
        var c = new BrainLoopCheckpoints();
        c.create(svc, "before");
        for (int i = 0; i < 10; i++) svc.cycle("X-" + i);
        c.create(svc, "after");
        var before = c.findByTag("before");
        var after = c.findByTag("after");
        assertThat(before.traceCount()).isZero();
        assertThat(after.traceCount()).isEqualTo(50);
    }

    @Test
    void clearRemovesAll() {
        var svc = new BrainLoopService();
        var c = new BrainLoopCheckpoints();
        c.create(svc, "a");
        c.create(svc, "b");
        c.clear();
        assertThat(c.size()).isZero();
    }
}
