package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W4 — Autonomy / Goals / Inbox integration tests.
 *
 * <p>Verifies goal CRUD + status evolution, and the inbox watcher ingests
 * text/CSV files into the persistent HDC store with idempotent ids.</p>
 */
class AutonomyIntegrationTest {

    @Test
    void goal_tracker_create_update_complete(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        assertThat(tracker.size()).isZero();

        GoalTracker.Goal g = tracker.addGoal("learn-cities", "Learn 50 capital cities");
        assertThat(g.id()).isNotBlank();
        assertThat(g.status()).isEqualTo(GoalTracker.Status.PENDING);
        assertThat(g.progress()).isEqualTo(0.0);
        assertThat(tracker.size()).isEqualTo(1);

        tracker.updateProgress(g.id(), 0.5);
        GoalTracker.Goal mid = tracker.get(g.id());
        assertThat(mid.progress()).isEqualTo(0.5);
        assertThat(mid.status()).isEqualTo(GoalTracker.Status.PENDING);

        tracker.updateProgress(g.id(), 1.0);
        GoalTracker.Goal done = tracker.get(g.id());
        assertThat(done.progress()).isEqualTo(1.0);
        assertThat(done.status()).isEqualTo(GoalTracker.Status.COMPLETED);
    }

    @Test
    void goal_tracker_progress_is_clamped(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal g = tracker.addGoal("clamp-test", "test clamping");
        tracker.updateProgress(g.id(), 1.5);  // over 1.0
        assertThat(tracker.get(g.id()).progress()).isEqualTo(1.0);
        tracker.updateProgress(g.id(), -0.5); // under 0.0
        assertThat(tracker.get(g.id()).progress()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void goal_tracker_mark_completed_and_abandoned(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal g = tracker.addGoal("abandon-test", "test abandon");
        tracker.markCompleted(g.id());
        assertThat(tracker.get(g.id()).status()).isEqualTo(GoalTracker.Status.COMPLETED);
        GoalTracker.Goal h = tracker.addGoal("abandon2", "test");
        tracker.markAbandoned(h.id());
        assertThat(tracker.get(h.id()).status()).isEqualTo(GoalTracker.Status.ABANDONED);
    }

    @Test
    void goal_tracker_list_returns_insertion_order(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        tracker.addGoal("alpha", "");
        tracker.addGoal("beta", "");
        tracker.addGoal("gamma", "");
        List<GoalTracker.Goal> all = tracker.listGoals();
        assertThat(all).hasSize(3);
        assertThat(all.get(0).name()).isEqualTo("alpha");
        assertThat(all.get(1).name()).isEqualTo("beta");
        assertThat(all.get(2).name()).isEqualTo("gamma");
    }

    @Test
    void goal_tracker_unknown_id_returns_null(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        assertThat(tracker.updateProgress("nonexistent", 0.5)).isNull();
        assertThat(tracker.markCompleted("nonexistent")).isNull();
        assertThat(tracker.get("nonexistent")).isNull();
    }

    @Test
    void goal_tracker_snapshot_format(@TempDir Path tmp) {
        GoalTracker tracker = new GoalTracker();
        tracker.addGoal("snap", "snapshot test");
        Map<String, Object> snap = tracker.snapshot();
        assertThat(snap.get("count")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> goals = (List<Map<String, Object>>) snap.get("goals");
        assertThat(goals).hasSize(1);
        assertThat(goals.get(0).get("name")).isEqualTo("snap");
        assertThat(goals.get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    void inbox_watcher_ingests_text_file(@TempDir Path tmp) throws Exception {
        Path mindDir = tmp.resolve("mind");
        Path inbox = mindDir.resolve("inbox");
        Files.createDirectories(inbox);
        Path hdcDir = mindDir.resolve("kb");
        Files.createDirectories(hdcDir);

        Path f = inbox.resolve("notes.txt");
        Files.writeString(f, "The mind can ingest plain-text notes into the HDC store.");

        PersistentHdcStore hdc = new PersistentHdcStore(hdcDir.resolve("kb.ndjson"), 256);
        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        int ingested = watcher.scan();

        assertThat(ingested).isEqualTo(1);
        assertThat(hdc.size()).isEqualTo(1);
        assertThat(hdc.snapshot().values().iterator().next())
            .contains("mind can ingest plain-text");
    }

    @Test
    void inbox_watcher_ingests_csv(@TempDir Path tmp) throws Exception {
        Path mindDir = tmp.resolve("mind");
        Path inbox = mindDir.resolve("inbox");
        Files.createDirectories(inbox);
        PersistentHdcStore hdc = new PersistentHdcStore(
            mindDir.resolve("kb.ndjson"), 256);

        Files.writeString(inbox.resolve("data.csv"),
            "city,country\nParis,France\nTokyo,Japan\nMoscow,Russia\n");

        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        watcher.scan();

        assertThat(hdc.size()).isGreaterThanOrEqualTo(1);
        // Verify the CSV data made it to the HDC store in some form.
        String allContent = String.join("\n", hdc.snapshot().values());
        assertThat(allContent).contains("Paris");
        assertThat(allContent).contains("Tokyo");
        assertThat(allContent).contains("Russia");
    }

    @Test
    void inbox_watcher_idempotent_on_rescan(@TempDir Path tmp) throws Exception {
        Path mindDir = tmp.resolve("mind");
        Path inbox = mindDir.resolve("inbox");
        Files.createDirectories(inbox);
        PersistentHdcStore hdc = new PersistentHdcStore(
            mindDir.resolve("kb.ndjson"), 256);
        Files.writeString(inbox.resolve("note.txt"), "Hello world");

        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        assertThat(watcher.scan()).isEqualTo(1);
        assertThat(watcher.scan()).isEqualTo(0);  // unchanged
        assertThat(hdc.size()).isEqualTo(1);
    }

    @Test
    void inbox_watcher_picks_up_new_file(@TempDir Path tmp) throws Exception {
        Path mindDir = tmp.resolve("mind");
        Path inbox = mindDir.resolve("inbox");
        Files.createDirectories(inbox);
        PersistentHdcStore hdc = new PersistentHdcStore(
            mindDir.resolve("kb.ndjson"), 256);

        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        assertThat(watcher.scan()).isEqualTo(0);
        Files.writeString(inbox.resolve("new.txt"), "Brand new content");
        assertThat(watcher.scan()).isEqualTo(1);
        assertThat(hdc.size()).isEqualTo(1);
    }

    @Test
    void inbox_watcher_handles_empty_directory(@TempDir Path tmp) {
        Path inbox = tmp.resolve("empty-inbox");
        // Don't even create the directory
        PersistentHdcStore hdc = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        assertThat(watcher.scan()).isZero();
        assertThat(hdc.size()).isZero();
    }

    @Test
    void inbox_watcher_status_snapshot(@TempDir Path tmp) {
        Path inbox = tmp.resolve("ib");
        PersistentHdcStore hdc = new PersistentHdcStore(
            tmp.resolve("kb.ndjson"), 256);
        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);
        Map<String, Object> snap = watcher.snapshot();
        assertThat(snap).containsKeys("inbox_dir", "last_ingest", "tracked_files");
        // tracked_files may be null or 0 before first scan (REC #6 RealInboxWatcher).
        Object tf = snap.get("tracked_files");
        assertThat(tf == null || tf.equals(0)).isTrue();
    }

    @Test
    void end_to_end_goal_then_inbox(@TempDir Path tmp) throws Exception {
        // Full integration: drop a file into inbox, watch it ingest, query back.
        Path mindDir = tmp.resolve("mind");
        Path inbox = mindDir.resolve("inbox");
        Files.createDirectories(inbox);
        PersistentHdcStore hdc = new PersistentHdcStore(
            mindDir.resolve("kb.ndjson"), 256);
        GoalTracker tracker = new GoalTracker();
        RealInboxWatcher watcher = new RealInboxWatcher(inbox, hdc);

        GoalTracker.Goal g = tracker.addGoal("ingest-facts", "Ingest 10 facts");
        Files.writeString(inbox.resolve("fact1.txt"),
            "The Sphinx of Giza was built around 2500 BC.");
        Files.writeString(inbox.resolve("fact2.txt"),
            "The Pacific Ocean is the largest ocean on Earth.");
        int n = watcher.scan();
        assertThat(n).isEqualTo(2);
        tracker.updateProgress(g.id(), 0.2);

        // Restart: re-read from disk
        PersistentHdcStore hdc2 = new PersistentHdcStore(
            mindDir.resolve("kb.ndjson"), 256);
        assertThat(hdc2.size()).isEqualTo(2);
    }
}
