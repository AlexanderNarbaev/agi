package io.matrix.api;

import io.matrix.brain.runtime.PersistentHdcStore;
import io.matrix.brain.runtime.RealInboxWatcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W2 #6 — RealInboxWatcher promoted.
 *
 * <p>Uses real AudioFFTEncoder (matrix-core) for audio, real
 * VisionEdgeEncoder (matrix-core) for images. Replaces local
 * InboxWatcher draft (D-8 cleanup).</p>
 */
class RealInboxWatcherWiringTest {

    @Test
    void gateway_uses_RealInboxWatcher_type() throws Exception {
        var f = MinimalHttpServer.class.getDeclaredField("inboxWatcher");
        f.setAccessible(true);
        assertThat(f.getType().getName())
            .isEqualTo("io.matrix.brain.runtime.RealInboxWatcher");
    }

    @Test
    void real_inbox_watcher_constructor_signature() throws Exception {
        // Verify the constructor signature is correct (Path + PersistentHdcStore).
        var ctor = RealInboxWatcher.class.getDeclaredConstructor(Path.class, PersistentHdcStore.class);
        assertThat(ctor).isNotNull();
    }

    @Test
    void real_inbox_watcher_snapshot_has_real_transcoders(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("hdc.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(tmp.resolve("inbox"), store);
        var snap = w.snapshot();
        // The snapshot names the REAL transcoder classes (matrix-core).
        assertThat(snap).containsKey("audio_transcoder");
        assertThat(snap).containsKey("image_transcoder");
        assertThat(snap.get("audio_transcoder")).isEqualTo("AudioFFTEncoder");
        assertThat(snap.get("image_transcoder")).isEqualTo("VisionEdgeEncoder");
    }

    @Test
    void real_inbox_watcher_scan_is_idempotent(@TempDir Path tmp) {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("hdc.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(tmp.resolve("inbox"), store);
        // Empty inbox: scan returns 0
        int n = w.scan();
        assertThat(n).isEqualTo(0);
        // Scan again: still 0
        int n2 = w.scan();
        assertThat(n2).isEqualTo(0);
    }

    @Test
    void real_inbox_watcher_ingests_text_file_to_persistent_mind(@TempDir Path tmp) throws Exception {
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("hdc.ndjson"), 256);
        java.nio.file.Path inbox = tmp.resolve("inbox");
        java.nio.file.Files.createDirectories(inbox);
        // Drop a text file
        java.nio.file.Files.writeString(inbox.resolve("note.txt"),
            "Paris is the capital of France");
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);
        int n = w.scan();
        assertThat(n).isGreaterThanOrEqualTo(1);
        // Verify the HDC store was fed
        var snap = store.snapshot();
        assertThat(snap.size()).isGreaterThan(0);
    }
}
