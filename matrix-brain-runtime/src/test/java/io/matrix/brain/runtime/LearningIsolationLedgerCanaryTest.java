package io.matrix.brain.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the CANARY of the quarantine ledger.
 *
 * <p><b>The defect this closes.</b> The quarantine ledger is machine state: it lives
 * next to the log it governs, under {@code data/mind/}, which is gitignored for the
 * same reason the log is. That is correct — the ledger is part of the machine's
 * state, not of the repository. But it meant that NOTHING in the repository guarded
 * it: a cleanup that removed {@code data/mind/} would take the ledger with it, the
 * admission gate would fall back to its documented default ("nothing is
 * quarantined"), all 226 records would be admitted again, and the contamination
 * would return <b>without a trace</b>.</p>
 *
 * <p><b>What the canary is.</b> A small TRACKED file,
 * {@code docs-v2/engineering/EPISTEMIC-LEARNING-QUARANTINE-CANARY.json}, that records
 * what the machine state was when the remediation ran: how many records the log
 * held, how many marks the ledger held, and the log's digest at that moment. The
 * test compares the machine with the record.</p>
 *
 * <p><b>Why the comparison is MONOTONIC.</b> The rule is "at least", never "exactly":
 * the log grows with every organic interaction and the ledger is append-only, so a
 * digest or an exact count would break the build on the next real conversation. The
 * digest in the canary is a HISTORICAL FACT — what the log hashed to when the
 * remediation ran — and it is deliberately NOT asserted.</p>
 *
 * <p><b>The rule, in one sentence.</b> If the real log exists and holds at least the
 * recorded number of records, then the ledger MUST exist and hold at least the
 * recorded number of marks. A missing ledger is then a failure, not a silent
 * fallback.</p>
 */
class LearningIsolationLedgerCanaryTest {

    /** The tracked record of the machine state, relative to the repository root. */
    private static final String CANARY_PATH =
        "docs-v2/engineering/EPISTEMIC-LEARNING-QUARANTINE-CANARY.json";

    /** The machine state the canary watches, relative to the repository root. */
    private static final String LOG_PATH = "data/mind/episodic.ndjson";

    /** The tracked record of the machine state. */
    record Canary(int recordedRecords,
                  int recordedMarks,
                  String logSha256AtRemediation,
                  String rule) {}

    /** The repository root: the canary and the machine state both live under it. */
    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path d = start; d != null; d = d.getParent()) {
            if (Files.isRegularFile(d.resolve(CANARY_PATH))) return d;
        }
        return null;
    }

    /** Reads the tracked canary. */
    private static Canary readCanary(Path root) throws Exception {
        JsonNode node = new ObjectMapper().readTree(root.resolve(CANARY_PATH).toFile());
        return new Canary(node.get("recordedRecords").asInt(),
            node.get("recordedMarks").asInt(),
            node.get("logSha256AtRemediation").asText(""),
            node.get("rule").asText(""));
    }

    /**
     * The check itself: the machine against the record.
     *
     * <p>Package-visible so that the test can prove it FAILS on a copy — the real
     * ledger is never touched to make a demonstration.</p>
     *
     * @param logPath    the machine's episodic log
     * @param ledgerPath the machine's quarantine ledger
     * @param canary     the tracked record of the machine state
     */
    static void verify(Path logPath, Path ledgerPath, Canary canary) throws Exception {
        if (!Files.isRegularFile(logPath)) {
            // A fresh clone has no machine state: there is nothing to check.
            return;
        }
        long records = Files.readAllLines(logPath).size();
        if (records < canary.recordedRecords()) {
            // The log is smaller than it was when the canary was recorded: this is
            // not the machine state the canary describes.
            return;
        }
        if (!Files.isRegularFile(ledgerPath)) {
            throw new AssertionError("the quarantine ledger is MISSING: " + ledgerPath
                + " — the log holds " + records + " records and the canary records "
                + canary.recordedMarks() + " quarantine marks, but the ledger that holds "
                + "them does not exist. Without it the admission gate falls back to "
                + "'nothing is quarantined', every record is admitted again, and the "
                + "contamination returns without a trace. Re-apply the remediation: "
                + "MATRIX_P0_4_REMEDIATE=true ./gradlew :matrix-brain-runtime:test "
                + "--tests \"io.matrix.brain.runtime.ProductionLearningIsolationRemediation\" "
                + "--offline");
        }
        int marks = new EpisodeQuarantine(ledgerPath).quarantinedIds().size();
        if (marks < canary.recordedMarks()) {
            throw new AssertionError("the quarantine ledger holds " + marks
                + " quarantine marks, and at least " + canary.recordedMarks()
                + " are required: the log holds " + records + " records and the marks "
                + "that keep the contaminated ones out of the learning feed are missing");
        }
    }

    @Test
    @DisplayName("the machine still has the quarantine ledger the canary records")
    void theMachineStillHasItsQuarantineLedger() throws Exception {
        Path root = repoRoot();
        assumeTrue(root != null,
            "the repository root could not be found: the canary lives at " + CANARY_PATH);
        Path logPath = root.resolve(LOG_PATH);
        assumeTrue(Files.isRegularFile(logPath),
            "this machine has no runtime episodic log (" + logPath + "): there is no "
                + "machine state for the canary to watch, and nothing to fail");

        Canary canary = readCanary(root);
        Path ledgerPath = EpisodeQuarantine.defaultLedgerPath(logPath);

        assertThatCode(() -> verify(logPath, ledgerPath, canary))
            .as("the machine state must still match the tracked canary")
            .doesNotThrowAnyException();

        System.out.printf("[CANARY] log=%s records=%d ledger=%s marks=%d (canary: >=%d "
                + "records, >=%d marks)%n",
            logPath, Files.readAllLines(logPath).size(), ledgerPath,
            new EpisodeQuarantine(ledgerPath).quarantinedIds().size(),
            canary.recordedRecords(), canary.recordedMarks());
    }

    /**
     * The demonstration that the canary FAILS when the ledger is missing — on a
     * COPY. The real ledger is never touched.
     */
    @Test
    @DisplayName("the canary FAILS when the ledger is missing (on a copy)")
    void theCanaryFailsWhenTheLedgerIsMissing() throws Exception {
        Path root = repoRoot();
        assumeTrue(root != null, "the repository root could not be found");
        Path realLog = root.resolve(LOG_PATH);
        assumeTrue(Files.isRegularFile(realLog),
            "this machine has no runtime episodic log: there is no machine state to copy");
        Canary canary = readCanary(root);

        // A copy of the machine state, with the ledger REMOVED — the cleanup scenario.
        Path work = Files.createTempDirectory("p04-canary-");
        Path copyLog = work.resolve("episodic.ndjson");
        Files.copy(realLog, copyLog, StandardCopyOption.REPLACE_EXISTING);
        Path copyLedger = EpisodeQuarantine.defaultLedgerPath(copyLog);
        Files.copy(EpisodeQuarantine.defaultLedgerPath(realLog), copyLedger,
            StandardCopyOption.REPLACE_EXISTING);
        Files.delete(copyLedger);

        assertThat(Files.isRegularFile(copyLedger)).isFalse();
        assertThatThrownBy(() -> verify(copyLog, copyLedger, canary))
            .as("a missing ledger must FAIL the canary, not fall back silently")
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("quarantine ledger is MISSING")
            .hasMessageContaining("falls back")
            .satisfies(e -> System.out.println("[CANARY] the demonstration of the "
                + "failure: " + e.getMessage()));

        // And the same copy WITH its ledger passes: the check is about the ledger.
        Files.copy(EpisodeQuarantine.defaultLedgerPath(realLog), copyLedger,
            StandardCopyOption.REPLACE_EXISTING);
        assertThatCode(() -> verify(copyLog, copyLedger, canary)).doesNotThrowAnyException();

        // A ledger with fewer marks than recorded also fails.
        List<String> lines = new ArrayList<>(Files.readAllLines(copyLedger));
        Files.writeString(copyLedger, String.join("\n", lines.subList(0, 3)) + "\n");
        assertThatThrownBy(() -> verify(copyLog, copyLedger, canary))
            .as("a truncated ledger must fail too")
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("quarantine marks");
    }
}
