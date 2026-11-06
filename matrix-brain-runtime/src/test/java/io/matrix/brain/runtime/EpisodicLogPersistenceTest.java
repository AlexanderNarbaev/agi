package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W38.0 — recovered from {@code SleepAndConsolidationIntegrationTest}, which existed only on
 * tag {@code checkpoint/feature-mind-w3-sleep-consolidation-20261009-230349} and in no branch.
 *
 * <h2>Why this file is three tests and not twelve</h2>
 * <p>The original had 12. Three exercise {@link EpisodicLog}, whose public API survived unchanged,
 * and are restored here verbatim in substance. The other nine build a
 * {@code ConsolidationCycle(2, 0.30, ttl)} and call {@code cycle.run(log, hdc)} returning a
 * {@code ConsolidationCycle.DreamReport}. Neither the constructor, nor {@code run}, nor the nested
 * {@code DreamReport} exists on the current class: the only {@code ConsolidationCycle} in the tree
 * is {@code io.matrix.lifecycle.ConsolidationCycle}, which has no {@code run(EpisodicLog,
 * PersistentHdcStore)} and no {@code DreamReport} at all. {@code SleepScheduler} was likewise
 * renamed to {@link RealSleepScheduler}, which takes {@code HierarchicalMemory} rather than a log
 * and a store.</p>
 *
 * <p>Those nine are preserved verbatim, uncompiled, at
 * {@code docs-v2/quality/lost-capability-tests/SleepAndConsolidationIntegrationTest.consolidation.java.txt}.
 * They are not {@code @Disabled} in place because that is a runtime annotation: {@code javac}
 * rejects the calls, and one uncompilable file fails the whole module's test compilation. That is
 * the same constraint recorded for {@code GpuKernelEngineIntegrationTest}.</p>
 *
 * <p>Restoring three real tests beats preserving twelve, and preserving nine beats losing them to
 * a {@code git gc}.</p>
 */
class EpisodicLogPersistenceTest {

    /**
     * Append then read is a round trip: order, count and field values all survive the file.
     *
     * <p>This is the load-bearing property of an append-only log that consolidation reads back.
     * If it fails, every downstream sleep behaviour is reading a different history than the one
     * that was written.</p>
     */
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

    /**
     * Append order is preserved.
     *
     * <p>Replaying episodes in a different order than they occurred would make "recency" a fiction,
     * so this asserts the index of each entry rather than merely the set membership — a set
     * comparison would pass for any permutation.</p>
     */
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

    /**
     * The on-disk format escapes quotes and newlines and re-parses to the original value.
     *
     * <p>An unescaped newline in a reply splits one NDJSON record into two, and the second half is
     * then parsed as a fresh record — which is how a malformed line becomes a fabricated fact.
     * The assertion is on the RE-PARSED value, not on the raw text, so an implementation that
     * escaped its output but could not read it back would still fail.</p>
     */
    @Test
    void episodic_log_ndjson_format_is_parseable(@TempDir Path tmp) throws Exception {
        Path logPath = tmp.resolve("ep-json.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        log.append("Hello, \"world\"!", "Line1\nLine2", 0.7, true,
                List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER"));
        String raw = Files.readString(logPath);
        assertThat(raw).endsWith("\n");
        assertThat(raw).contains("\\\"world\\\"");
        assertThat(raw).contains("\\n");

        EpisodicLog log2 = new EpisodicLog(logPath);
        assertThat(log2.readAll()).hasSize(1);
        assertThat(log2.readAll().get(0).reply()).isEqualTo("Line1\nLine2");
    }
}