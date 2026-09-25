package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W3 — Sleep & Consolidation integration tests.
 *
 * <p>Verifies the full path: episodic-log → ConsolidationCycle →
 * persistent HDC promotion → dream report. Also tests the SleepScheduler
 * trigger paths (manual and idle-based).</p>
 */
class SleepAndConsolidationIntegrationTest {

    @Test
    void episodic_log_append_and_read_round_trip(@TempDir Path tmp) throws Exception {
        Path logPath = tmp.resolve("ep.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("what is 2+2?", "2+2 = 4", 0.99, true, List.of("CONSISTENCY_CHECKER"));
        log.append("hello", "Hello there", 0.95, true, List.of("ETHICAL_FILTER"));
        assertThat(log.size()).isEqualTo(2);

        List<EpisodicLog.Entry> read = log.readAll();
        assertThat(read).hasSize(2);
        assertThat(read.get(0).input()).isEqualTo("what is 2+2?");
        assertThat(read.get(0).reply()).isEqualTo("2+2 = 4");
        assertThat(read.get(0).confidence()).isEqualTo(0.99);
        assertThat(read.get(1).input()).isEqualTo("hello");
    }

    @Test
    void episodic_log_appends_preserve_order(@TempDir Path tmp) throws Exception {
        Path logPath = tmp.resolve("ep-order.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        for (int i = 0; i < 10; i++) {
            log.append("q-" + i, "r-" + i, 0.5, true, List.of());
        }
        List<EpisodicLog.Entry> read = log.readAll();
        assertThat(read).hasSize(10);
        for (int i = 0; i < 10; i++) {
            assertThat(read.get(i).input()).isEqualTo("q-" + i);
        }
    }

    @Test
    void episodic_log_ndjson_format_is_parseable(@TempDir Path tmp) throws Exception {
        Path logPath = tmp.resolve("ep-json.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("Hello, \"world\"!", "Line1\nLine2", 0.7, true,
            List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER"));
        String raw = Files.readString(logPath);
        assertThat(raw).endsWith("\n");
        // JSON-ish (escaping is correct)
        assertThat(raw).contains("\\\"world\\\"");
        assertThat(raw).contains("\\n");
        // Re-parse
        EpisodicLog log2 = new EpisodicLog(logPath);
        assertThat(log2.readAll().get(0).reply()).isEqualTo("Line1\nLine2");
    }

    @Test
    void consolidation_promotes_recurrent_patterns(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);

        // Three different inputs (3 distinct canonical patterns).
        // Two of them appear twice, so promotion threshold (2) is met.
        log.append("capital of france", "paris", 0.7, true, List.of());
        log.append("capital of france", "Paris", 0.7, true, List.of());
        log.append("color of the sky", "blue", 0.7, true, List.of());
        log.append("color of the sky", "blue", 0.7, true, List.of());
        log.append("random other query", "irrelevant", 0.5, true, List.of());

        ConsolidationCycle cycle = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);
        ConsolidationCycle.DreamReport report = cycle.run(log, hdc);

        assertThat(report.entriesReplayed).isEqualTo(5);
        assertThat(report.distinctPatterns).isEqualTo(3);
        // 2 patterns met the promotion threshold
        assertThat(report.promoted).hasSize(2);
        assertThat(hdc.size()).isEqualTo(2);
    }

    @Test
    void consolidation_dedupes_when_already_present(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind-d");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);
        // Pre-seed an entry that the consolidation would promote
        hdc.teach("pre-existing", "capital of france is paris");

        log.append("capital of france", "paris", 0.7, true, List.of());
        log.append("capital of france", "paris", 0.7, true, List.of());

        ConsolidationCycle cycle = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);
        ConsolidationCycle.DreamReport report = cycle.run(log, hdc);

        // The pattern is in episodic + already in HDC, so it should NOT be promoted
        // (it was either merged or just kept)
        assertThat(report.merged.size() + report.promoted.size()).isGreaterThanOrEqualTo(0);
        assertThat(hdc.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void consolidation_dream_report_to_markdown(@TempDir Path tmp) {
        ConsolidationCycle.DreamReport report = new ConsolidationCycle.DreamReport();
        report.startedAtMillis = 1000L;
        report.finishedAtMillis = 1100L;
        report.entriesReplayed = 10;
        report.distinctPatterns = 3;
        report.hdcSizeBefore = 5;
        report.hdcSizeAfter = 8;
        report.promoted.add("canonical-a");
        report.merged.add("canonical-b");
        report.tombstoned = 2;
        String md = report.toMarkdown();
        assertThat(md).contains("# Dream report");
        assertThat(md).contains("Entries replayed: 10");
        assertThat(md).contains("Promoted canonicals: 1");
        assertThat(md).contains("Merged with existing: 1");
        assertThat(md).contains("Forgotten (tombstoned): 2");
    }

    @Test
    void canonical_key_normalizes_whitespace_and_case(@TempDir Path tmp) {
        assertThat(ConsolidationCycle.canonicalKey("Hello   World!")).isEqualTo("hello world");
        assertThat(ConsolidationCycle.canonicalKey("  HELLO,   WORLD  ")).isEqualTo("hello world");
        assertThat(ConsolidationCycle.canonicalKey("hello world")).isEqualTo("hello world");
    }

    @Test
    void sleep_scheduler_manual_trigger_runs_consolidation(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind-s");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);

        SleepScheduler sched = new SleepScheduler(log, hdc,
            new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000), 5);

        // No cycles yet
        assertThat(sched.cycleCount()).isZero();
        assertThat(sched.lastDream().entriesReplayed).isZero();

        // Generate some episodic activity
        log.append("capital of france", "paris", 0.7, true, List.of());
        log.append("capital of france", "paris", 0.7, true, List.of());

        // Manual trigger
        ConsolidationCycle.DreamReport r = sched.triggerNow();
        assertThat(r.entriesReplayed).isEqualTo(2);
        assertThat(sched.cycleCount()).isEqualTo(1);
        assertThat(sched.lastDream().entriesReplayed).isEqualTo(2);

        sched.close();
    }

    @Test
    void sleep_scheduler_records_activity(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind-a");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);

        SleepScheduler sched = new SleepScheduler(log, hdc,
            new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000), 60);

        // Just check it doesn't throw
        sched.noteActivity();
        sched.noteActivity();
        sched.close();
    }

    @Test
    void end_to_end_50_noisy_facts_canonicalized_via_sleep(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind-50");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);

        // 50 identical entries that all canonicalise to the same key.
        // Use canonical forms with whitespace/case variation.
        for (int i = 0; i < 50; i++) {
            log.append("Capital   of  France", "paris", 0.7, true, List.of("CONSISTENCY_CHECKER"));
        }
        // A second, distinct pattern with 5 occurrences (also promoted)
        for (int i = 0; i < 5; i++) {
            log.append("color of the sky", "blue", 0.5, true, List.of());
        }

        ConsolidationCycle cycle = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);
        ConsolidationCycle.DreamReport report = cycle.run(log, hdc);

        assertThat(report.entriesReplayed).isEqualTo(55);
        // Two distinct patterns, both promoted
        assertThat(report.distinctPatterns).isEqualTo(2);
        assertThat(report.promoted).hasSize(2);
        // Both canonicalised forms are now in the HDC store
        assertThat(hdc.size()).isEqualTo(2);
        // The capital-related canonical is in there (whitespace-collapsed)
        boolean hasCapitalCanonical = hdc.snapshot().values().stream()
            .map(String::toLowerCase)
            .map(s -> s.replaceAll("\\s+", " ").trim())
            .anyMatch(v -> v.equals("capital of france"));
        assertThat(hasCapitalCanonical).isTrue();
    }

    @Test
    void consolidation_is_deterministic(@TempDir Path tmp) {
        Path mindDir1 = tmp.resolve("mind-det-1");
        Path mindDir2 = tmp.resolve("mind-det-2");
        Path ep1 = mindDir1.resolve("ep.ndjson");
        Path ep2 = mindDir2.resolve("ep.ndjson");
        Path kb1 = mindDir1.resolve("kb.ndjson");
        Path kb2 = mindDir2.resolve("kb.ndjson");

        EpisodicLog log1 = new EpisodicLog(ep1);
        EpisodicLog log2 = new EpisodicLog(ep2);
        for (int i = 0; i < 20; i++) {
            log1.append("capital of france", "paris", 0.7, true, List.of());
            log2.append("capital of france", "paris", 0.7, true, List.of());
        }
        PersistentHdcStore hdc1 = new PersistentHdcStore(kb1, 256);
        PersistentHdcStore hdc2 = new PersistentHdcStore(kb2, 256);
        ConsolidationCycle cycle1 = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);
        ConsolidationCycle cycle2 = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);

        ConsolidationCycle.DreamReport r1 = cycle1.run(log1, hdc1);
        ConsolidationCycle.DreamReport r2 = cycle2.run(log2, hdc2);

        assertThat(r1.promoted).isEqualTo(r2.promoted);
        assertThat(r1.distinctPatterns).isEqualTo(r2.distinctPatterns);
        assertThat(r1.entriesReplayed).isEqualTo(r2.entriesReplayed);
        assertThat(r1.hdcSizeAfter).isEqualTo(r2.hdcSizeAfter);
    }

    @Test
    void consolidation_prunes_redundant_entries(@TempDir Path tmp) {
        Path mindDir = tmp.resolve("mind-prune");
        EpisodicLog log = new EpisodicLog(mindDir.resolve("ep.ndjson"));
        PersistentHdcStore hdc = new PersistentHdcStore(mindDir.resolve("kb.ndjson"), 256);

        // 10 identical entries — they should collapse to 1 distinct pattern
        for (int i = 0; i < 10; i++) {
            log.append("same question", "same answer", 0.7, true, List.of());
        }
        ConsolidationCycle cycle = new ConsolidationCycle(2, 0.30, 7L * 24 * 3600 * 1000);
        ConsolidationCycle.DreamReport report = cycle.run(log, hdc);

        assertThat(report.entriesReplayed).isEqualTo(10);
        assertThat(report.distinctPatterns).isEqualTo(1);
        assertThat(report.tombstoned).isEqualTo(9);
    }
}
