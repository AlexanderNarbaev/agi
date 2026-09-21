package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 183 — BrainSnapshot unit tests. */
class BrainSnapshotTest {

    @Test
    void captureFromEmptyService() {
        BrainLoopService svc = new BrainLoopService();
        var snap = BrainSnapshot.capture(svc);
        assertThat(snap.traceCount()).isZero();
        assertThat(snap.arousal()).isEqualTo(0.3);
    }

    @Test
    void captureFromActiveService() {
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 10; i++) svc.cycle("Q-" + i);
        var snap = BrainSnapshot.capture(svc);
        assertThat(snap.traceCount()).isEqualTo(50); // 10 × 5
        assertThat(snap.traceHead()).isNotEmpty();
        assertThat(snap.traceTail()).isNotEmpty();
    }

    @Test
    void jsonRoundtrip() {
        var snap = new BrainSnapshot.Snapshot(0.5, 100, "abc", "xyz", 12345L);
        String json = BrainSnapshot.toJson(snap);
        var reloaded = BrainSnapshot.fromJson(json);
        assertThat(reloaded.arousal()).isEqualTo(0.5);
        assertThat(reloaded.traceCount()).isEqualTo(100);
        assertThat(reloaded.traceHead()).isEqualTo("abc");
        assertThat(reloaded.traceTail()).isEqualTo("xyz");
        assertThat(reloaded.createdAtMillis()).isEqualTo(12345L);
    }

    @Test
    void fileRoundtrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("snap.json");
        var snap = new BrainSnapshot.Snapshot(0.7, 200, "h", "t", 99999L);
        BrainSnapshot.save(file, snap);
        var loaded = BrainSnapshot.load(file);
        assertThat(loaded.arousal()).isEqualTo(0.7);
        assertThat(loaded.traceCount()).isEqualTo(200);
    }

    @Test
    void snapshotRecord() {
        var snap = new BrainSnapshot.Snapshot(0.5, 50, "head", "tail", 1000L);
        assertThat(snap.arousal()).isEqualTo(0.5);
        assertThat(snap.traceCount()).isEqualTo(50);
        assertThat(snap.traceHead()).isEqualTo("head");
        assertThat(snap.traceTail()).isEqualTo("tail");
        assertThat(snap.createdAtMillis()).isEqualTo(1000L);
    }

    @Test
    void saveAndLoadSameFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("snap.json");
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 5; i++) svc.cycle("test-" + i);
        BrainSnapshot.save(file, BrainSnapshot.capture(svc));
        var loaded = BrainSnapshot.load(file);
        assertThat(loaded.traceCount()).isEqualTo(25);
    }
}
