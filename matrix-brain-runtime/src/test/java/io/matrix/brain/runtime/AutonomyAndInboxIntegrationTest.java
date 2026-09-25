package io.matrix.brain.runtime;

import io.matrix.brain.BirBrainCycle;
import io.matrix.knowledge.SimpleKnowledgeBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W4 — Autonomy + inbox (real transcoders) tests.
 */
class AutonomyAndInboxIntegrationTest {

    private static BirBrainCycle brain() {
        return new BirBrainCycle(new Random(42L));
    }

    // ---------- AutonomyLoop ----------

    @Test
    void autonomy_loop_proposes_goal_and_completes_it() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            int id = loop.proposeGoal("learn capitals of 50 countries", 5);
            assertThat(loop.goals().activeGoals()).hasSize(1);
            assertThat(loop.goals().activeGoals().get(0).id()).isEqualTo(id);

            loop.completeGoal(id);
            assertThat(loop.goals().activeGoals()).isEmpty();
            assertThat(loop.goals().completedCount()).isEqualTo(1);
        } finally {
            loop.close();
        }
    }

    @Test
    void autonomy_loop_reflection_returns_engine_tagged_report() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            loop.start();
            loop.noteActivity();
            // Force a reflection tick.
            var report = loop.reflect();
            assertThat(report).isNotNull();
            assertThat(report.cycleCount()).isGreaterThanOrEqualTo(0L);
            assertThat(report.arousal()).isGreaterThanOrEqualTo(0.0);
        } finally {
            loop.close();
        }
    }

    @Test
    void autonomy_loop_snapshot_includes_real_engine_fields() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            loop.proposeGoal("test goal", 3);
            loop.start();
            var snap = loop.snapshot();
            assertThat(snap).containsKeys(
                "running", "cycle_count", "total_reflections",
                "arousal", "active_goals", "completed_goals",
                "abandoned_goals", "last_reflection"
            );
            assertThat(((Number) snap.get("active_goals")).intValue()).isEqualTo(1);
        } finally {
            loop.close();
        }
    }

    @Test
    void autonomy_loop_arousal_resets_on_reflection() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            loop.noteActivity();
            loop.noteActivity();
            double before = loop.arousal().getArousal();
            assertThat(before).isGreaterThanOrEqualTo(0.0);
            loop.reflect();
            double after = loop.arousal().getArousal();
            assertThat(after).isLessThanOrEqualTo(before + 1e-9);
        } finally {
            loop.close();
        }
    }

    @Test
    void autonomy_loop_abandon_goal_drops_active_count() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            int id = loop.proposeGoal("test abandon", 1);
            loop.abandonGoal(id);
            assertThat(loop.goals().activeGoals()).isEmpty();
        } finally {
            loop.close();
        }
    }

    @Test
    void autonomy_loop_exposes_real_emergence_analyzer() {
        AutonomyLoop loop = new AutonomyLoop(brain());
        try {
            assertThat(loop.emergence()).isNotNull();
            assertThat(loop.emergence().seed()).isEqualTo(42L);
        } finally {
            loop.close();
        }
    }

    // ---------- RealInboxWatcher ----------

    @Test
    void real_inbox_ingests_text_file(@TempDir Path tmp) throws IOException {
        Path inbox = tmp.resolve("inbox");
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("note.txt"), "The capital of France is Paris");

        Path kb = tmp.resolve("kb.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);

        int n = w.scan();
        assertThat(n).isEqualTo(1);
        assertThat(store.size()).isEqualTo(1);
        String content = store.snapshot().values().iterator().next();
        assertThat(content).contains("capital of France");
        assertThat(content).contains("inbox:note.txt");
    }

    @Test
    void real_inbox_transcodes_audio_via_AudioFFTEncoder(@TempDir Path tmp) throws IOException {
        Path inbox = tmp.resolve("inbox");
        Files.createDirectories(inbox);
        // Write 1024 bytes of varying values to act as a synthetic .wav file
        byte[] wav = new byte[1024];
        for (int i = 0; i < wav.length; i++) wav[i] = (byte) ((i * 37) % 256);
        Files.write(inbox.resolve("sound.wav"), wav);

        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);

        int n = w.scan();
        assertThat(n).isEqualTo(1);
        String content = store.snapshot().values().iterator().next();
        // Real transcoder produces "audio:frame=N hdc_dim=256 total_energy=X"
        assertThat(content).contains("audio:");
        assertThat(content).contains("hdc_dim=256");
        assertThat(content).contains("total_energy=");
    }

    @Test
    void real_inbox_transcodes_image_via_VisionEdgeEncoder(@TempDir Path tmp) throws IOException {
        Path inbox = tmp.resolve("inbox");
        Files.createDirectories(inbox);
        // Write 1024 bytes as synthetic image data
        byte[] img = new byte[1024];
        for (int i = 0; i < img.length; i++) img[i] = (byte) ((i * 53 + 17) % 256);
        Files.write(inbox.resolve("photo.png"), img);

        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);

        int n = w.scan();
        assertThat(n).isEqualTo(1);
        String content = store.snapshot().values().iterator().next();
        assertThat(content).contains("image:");
        assertThat(content).contains("primitives=");
        assertThat(content).contains("hdc_dim=256");
    }

    @Test
    void real_inbox_is_idempotent_on_rescan(@TempDir Path tmp) throws IOException {
        Path inbox = tmp.resolve("inbox");
        Files.createDirectories(inbox);
        Files.writeString(inbox.resolve("doc.md"), "hello");

        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);

        assertThat(w.scan()).isEqualTo(1);
        assertThat(w.scan()).isEqualTo(0);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void real_inbox_handles_empty_directory(@TempDir Path tmp) {
        Path inbox = tmp.resolve("none");
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);
        assertThat(w.scan()).isZero();
    }

    @Test
    void real_inbox_snapshot_reports_real_transcoder_names(@TempDir Path tmp) {
        Path inbox = tmp.resolve("inbox");
        PersistentHdcStore store = new PersistentHdcStore(tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher w = new RealInboxWatcher(inbox, store);
        var snap = w.snapshot();
        assertThat(snap.get("audio_transcoder")).isEqualTo("AudioFFTEncoder");
        assertThat(snap.get("image_transcoder")).isEqualTo("VisionEdgeEncoder");
    }
}
