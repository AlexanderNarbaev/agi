package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 259 — SnapshotsStore unit tests. */
class BrainLoopSnapshotsStoreTest {

    @Test
    void emptyStore() {
        var s = new BrainLoopSnapshotsStore();
        assertThat(s.size()).isZero();
    }

    @Test
    void storeAndRetrieve() {
        var svc = new BrainLoopService();
        var s = new BrainLoopSnapshotsStore();
        s.store(svc, "init");
        assertThat(s.size()).isEqualTo(1);
    }

    @Test
    void findByTag() {
        var svc = new BrainLoopService();
        var s = new BrainLoopSnapshotsStore();
        s.store(svc, "start");
        s.store(svc, "after-1");
        var found = s.findByTag("after-1");
        assertThat(found).isNotNull();
        assertThat(found.tag()).isEqualTo("after-1");
    }

    @Test
    void evictionAtMax() {
        var svc = new BrainLoopService();
        var s = new BrainLoopSnapshotsStore(3);
        for (int i = 0; i < 5; i++) s.store(svc, "snap-" + i);
        assertThat(s.size()).isEqualTo(3);
        var list = s.list();
        // Oldest (snap-0, snap-1) should be evicted
        assertThat(list.get(0).tag()).isEqualTo("snap-2");
    }

    @Test
    void clearRemovesAll() {
        var svc = new BrainLoopService();
        var s = new BrainLoopSnapshotsStore();
        s.store(svc, "a");
        s.store(svc, "b");
        s.clear();
        assertThat(s.size()).isZero();
    }
}
