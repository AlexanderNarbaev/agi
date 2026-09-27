package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W3 Part B Step 6 — Restart-Survival Test (D-13).
 */
class RestartSurvivalTest {

    @Test
    void episodic_log_survives_close_and_reopen(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("episodic.ndjson");

        EpisodicLog log1 = new EpisodicLog(dbPath);
        for (int i = 0; i < 20; i++) {
            log1.append("Paris " + i, "Yes capital", 0.95, true, List.of());
        }

        EpisodicLog log2 = new EpisodicLog(dbPath);
        List<EpisodicLog.Entry> entries = log2.readAll();
        assertThat(entries).hasSize(20);
        assertThat(entries.get(0).input()).isEqualTo("Paris 0");
    }

    @Test
    void rules_learned_via_induction_have_same_ids_across_reload(@TempDir Path tmp) throws Exception {
        // Same seed + same input episodes ⇒ same rule IDs (Article III determinism).
        Path dbPath = tmp.resolve("episodic.ndjson");
        BirRegistry registry1 = new BirRegistry();
        RuleInductionEngine engine = new RuleInductionEngine(42L, registry1);
        EpisodeFeatureExtractor features = new EpisodeFeatureExtractor();

        // Session 1: write + sleep.
        EpisodicLog log1 = new EpisodicLog(dbPath);
        for (int i = 0; i < 18; i++) {
            log1.append("Paris " + i, "Yes", 0.95, true, List.of());
        }
        for (int i = 0; i < 2; i++) {
            log1.append("noisy " + i, "no", 0.20, false, List.of());
        }
        RealSleepScheduler sched1 = new RealSleepScheduler(
            new io.matrix.memory.HierarchicalMemory(),
            new io.matrix.lifecycle.ConsolidationCycle(),
            new io.matrix.federation.Anonymizer(2),
            5,
            log1, registry1, engine, features);
        sched1.triggerNow();
        String[] ruleIds1 = registry1.listAll().stream()
            .map(BirRegistry.Entry::id).toArray(String[]::new);

        // Session 2: reopen + sleep.
        BirRegistry registry2 = new BirRegistry();
        RuleInductionEngine engine2 = new RuleInductionEngine(42L, registry2);
        EpisodicLog log2 = new EpisodicLog(dbPath);
        RealSleepScheduler sched2 = new RealSleepScheduler(
            new io.matrix.memory.HierarchicalMemory(),
            new io.matrix.lifecycle.ConsolidationCycle(),
            new io.matrix.federation.Anonymizer(2),
            5,
            log2, registry2, engine2, features);
        sched2.triggerNow();
        String[] ruleIds2 = registry2.listAll().stream()
            .map(BirRegistry.Entry::id).toArray(String[]::new);

        // Article III: same seed + same episodes ⇒ same rule IDs.
        assertThat(ruleIds1).containsExactlyInAnyOrder(ruleIds2);
    }

    @Test
    void episodic_log_size_grows_monotonically_across_sessions(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("episodic.ndjson");

        EpisodicLog log1 = new EpisodicLog(dbPath);
        for (int i = 0; i < 10; i++) {
            log1.append("session1-" + i, "ok", 0.9, true, List.of());
        }

        EpisodicLog log2 = new EpisodicLog(dbPath);
        for (int i = 0; i < 5; i++) {
            log2.append("session2-" + i, "ok", 0.9, true, List.of());
        }

        EpisodicLog log3 = new EpisodicLog(dbPath);
        assertThat(log3.readAll()).hasSize(15);
    }

    @Test
    void episodic_persistence_path_is_writable(@TempDir Path tmp) {
        Path dbPath = tmp.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(dbPath);
        log.append("test", "ok", 0.9, true, List.of());
        
        // After flush(), the file should exist on disk
        assertThat(Files.exists(dbPath)).isTrue();
    }
}
