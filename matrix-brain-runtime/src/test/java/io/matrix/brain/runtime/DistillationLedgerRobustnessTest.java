package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W34.1 — two defects the W32.34 test recovery surfaced in {@link DistillationLedger}.
 *
 * <p>Both were found by integration tests that had not executed in weeks. They are recorded
 * here as first-class contracts rather than as fixes, because the failure modes they describe
 * are the interesting part: an audit trail that cannot survive one bad line, and a ledger
 * that records its own impact but cannot report it.</p>
 *
 * <h2>Defect 1 — one corrupt line destroys the whole audit trail</h2>
 * <p>{@code readAll()} called {@code Integer.parseInt(extract(...))} unguarded. A line missing a
 * numeric field yields {@code ""} from {@code extract}, which throws
 * {@code NumberFormatException} and aborts the entire read. One torn line therefore discards
 * every <em>valid</em> entry with it. For a ledger — the thing that exists so that past work
 * can be accounted for — losing the record because one line was damaged is backwards.</p>
 *
 * <h2>Defect 2 — the ledger records what it induced, then cannot report it</h2>
 * <p>Every NDJSON line persists {@code birClausesSynthesized}, and {@code readAll()} parses it
 * back. But {@code summary()} aggregated only {@code runs} and {@code total_inputs_bytes}, so
 * the one number that says "did distillation actually teach anything" was recorded and never
 * surfaced. Nothing was lost — it was simply never summed.</p>
 */
class DistillationLedgerRobustnessTest {

    /** A well-formed ledger line, used as the baseline for corruption experiments. */
    private static final String GOOD_LINE =
            "{\"sourceId\":\"src\",\"datasetOrPattern\":\"p\",\"inputBits\":10,"
            + "\"samplesUsed\":2,\"hdcPromoted\":1,\"birClausesSynthesized\":3,"
            + "\"tsetlinLiterals\":4,\"fidelity\":0.9,\"durationMs\":5,"
            + "\"timestampMs\":42,\"artifactHash\":\"abc\"}";

    private static void write(Path p, String... lines) throws Exception {
        Files.write(p, List.of(lines), StandardCharsets.UTF_8);
    }

    // ==================================================================
    // Defect 1 — graceful corrupt-line handling
    // ==================================================================

    @Test
    @DisplayName("a corrupt line is skipped, and the GOOD entries still come back")
    void corrupt_line_does_not_destroy_good_entries(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, GOOD_LINE,
                 "{\"sourceId\":\"broken\",\"samplesUsed\":",   // truncated mid-write
                 GOOD_LINE);
        var ledger = new DistillationLedger(f);

        // The headline defect: this THREW before the fix.
        assertThatCode(ledger::readAll).doesNotThrowAnyException();
        List<DistillationLedger.Entry> all = ledger.readAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).sourceId()).isEqualTo("src");
        assertThat(all.get(1).sourceId()).isEqualTo("src");
    }

    @Test
    @DisplayName("a line missing a numeric field is skipped, not fatal")
    void missing_numeric_field_is_skipped(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        // "birClausesSynthesized" is absent entirely: extract() returns "" and
        // Integer.parseInt("") is what used to throw.
        write(f, "{\"sourceId\":\"x\",\"samplesUsed\":2,\"hdcPromoted\":1}",
                 GOOD_LINE);
        var ledger = new DistillationLedger(f);
        assertThatCode(ledger::readAll).doesNotThrowAnyException();
        assertThat(ledger.readAll()).hasSize(1);
    }

    @Test
    @DisplayName("a ledger of ONLY corrupt lines returns empty rather than throwing")
    void all_corrupt_returns_empty(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, "{", "}", "{\"samplesUsed\":}", "");
        var ledger = new DistillationLedger(f);
        assertThatCode(ledger::readAll).doesNotThrowAnyException();
        assertThat(ledger.readAll()).isEmpty();
    }

    @Test
    @DisplayName("a value-less JSON object does not make extract() throw")
    void empty_object_does_not_throw(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, "{\"sourceId\":\"\",\"datasetOrPattern\":\"\"}");
        var ledger = new DistillationLedger(f);
        assertThatCode(ledger::readAll).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("size() survives a corrupt ledger")
    void size_survives_corruption(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, GOOD_LINE, "{\"broken\":");
        var ledger = new DistillationLedger(f);
        assertThatCode(ledger::size).doesNotThrowAnyException();
    }

    // ==================================================================
    // Defect 2 — the ledger reports its own impact
    // ==================================================================

    @Test
    @DisplayName("summary() aggregates total_bir_clauses_induced across runs")
    void summary_aggregates_bir_clauses(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f,
              lineWith("birClausesSynthesized", 2),
              lineWith("birClausesSynthesized", 5),
              lineWith("birClausesSynthesized", 1));
        Map<String, Object> s = new DistillationLedger(f).summary();

        assertThat(s.get("runs")).isEqualTo(3);
        // This key was absent, which is what made the recovered integration test NPE on
        // ((Number) s.get(...)).intValue() rather than reporting a small wrong number.
        assertThat(s.get("total_bir_clauses_induced"))
                .isNotNull()
                .isEqualTo(8L);
    }

    @Test
    @DisplayName("summary() still reports total_inputs_bytes (no regression)")
    void summary_keeps_existing_keys(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, lineWith("birClausesSynthesized", 4));
        Map<String, Object> s = new DistillationLedger(f).summary();
        assertThat(s).containsKeys("runs", "total_inputs_bytes", "path");
        assertThat(s.get("path")).isEqualTo(f.toString());
    }

    @Test
    @DisplayName("an empty ledger aggregates to zero, not to a missing key")
    void empty_ledger_sums_to_zero(@TempDir Path tmp) throws Exception {
        Map<String, Object> s =
                new DistillationLedger(tmp.resolve("nothing.ndjson")).summary();
        assertThat(s.get("runs")).isEqualTo(0);
        assertThat(s.get("total_bir_clauses_induced")).isEqualTo(0L);
        assertThat(s.get("total_inputs_bytes")).isEqualTo(0L);
    }

    @Test
    @DisplayName("a corrupt line makes summary() PARTIAL, not silently short")
    void corrupt_lines_mark_the_summary_partial(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("l.ndjson");
        write(f, GOOD_LINE, "{\"truncated\":");
        Map<String, Object> s = new DistillationLedger(f).summary();
        // Honesty: the total is understated, so it must say so rather than look complete.
        assertThat(s.get("partial")).isEqualTo(true);
    }

    private static String lineWith(String field, int value) {
        return "{\"sourceId\":\"s\",\"datasetOrPattern\":\"p\",\"inputBits\":10,"
                + "\"samplesUsed\":2,\"hdcPromoted\":1,\"" + field + "\":" + value + ","
                + "\"tsetlinLiterals\":4,\"fidelity\":0.9,\"durationMs\":5,"
                + "\"timestampMs\":42,\"artifactHash\":\"abc\"}";
    }
}