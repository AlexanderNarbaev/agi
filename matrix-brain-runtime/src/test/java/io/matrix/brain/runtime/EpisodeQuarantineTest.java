package io.matrix.brain.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the quarantine is a reversible MARK, never a deletion.
 *
 * <p><b>The rule this test enforces.</b> A contaminated record must stay in the
 * durable log, byte for byte, and stay readable, while it is excluded from the
 * learning window. The mechanism is an append-only ledger next to the log; the
 * reversal is an append, not an edit, so the quarantine itself remains in the
 * history.</p>
 *
 * <p><b>Why it matters.</b> The alternative — deleting the rows — destroys the audit
 * trail and is indistinguishable from a cover-up. The alternative to quarantining —
 * leaving them in the window — is what made the mind learn from its own test paper.
 * Only a reversible mark satisfies both.</p>
 */
class EpisodeQuarantineTest {

    /** Write a legacy-shaped record (no role tag) straight to the log. */
    private static void writeLegacy(Path logPath, String id, String input, double confidence,
                                    boolean accepted) throws Exception {
        String line = "{\"id\":\"" + id + "\",\"input\":\"" + input + "\",\"reply\":\"r\","
            + "\"confidence\":" + confidence + ",\"accepted\":" + accepted
            + ",\"modulators\":[],\"ts\":1}";
        Files.writeString(logPath, line + "\n", StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    @DisplayName("quarantining excludes the record without touching the log's bytes")
    void quarantineExcludesWithoutTouchingTheLog(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic question", 0.9, true);
        writeLegacy(logPath, "ep-2", "stolitsa frantsii", 0.9, true);
        String bytesBefore = Files.readString(logPath);

        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        quarantine.quarantine("ep-2", "frozen evaluation probe", 1000L);

        assertThat(Files.readString(logPath))
            .as("the log must be byte-identical: a quarantine is a mark, not an edit")
            .isEqualTo(bytesBefore);

        EpisodicLog log = new EpisodicLog(logPath);
        assertThat(log.readAll()).as("both records stay auditable").hasSize(2);
        assertThat(log.learningCandidates()).hasSize(1);
        assertThat(log.learningCandidates().get(0).id()).isEqualTo("ep-1");
        assertThat(log.learningWindow()).hasSize(1);
        assertThat(log.learningWindow().get(0).id()).isEqualTo("ep-1");
    }

    @Test
    @DisplayName("the quarantine is reversible and the reversal is itself in the history")
    void theQuarantineIsReversible(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "stolitsa frantsii", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));

        quarantine.quarantine("ep-1", "probe", 1000L);
        assertThat(quarantine.isQuarantined("ep-1")).isTrue();
        assertThat(new EpisodicLog(logPath).learningCandidates()).isEmpty();

        quarantine.release("ep-1", "operator decision: admitted after review", 2000L);
        assertThat(quarantine.isQuarantined("ep-1")).isFalse();
        assertThat(new EpisodicLog(logPath).learningCandidates()).hasSize(1);

        // The history keeps BOTH events: the quarantine is not erased by the release.
        List<EpisodeQuarantine.Event> events = quarantine.events();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).op()).isEqualTo(EpisodeQuarantine.Op.QUARANTINE);
        assertThat(events.get(1).op()).isEqualTo(EpisodeQuarantine.Op.RELEASE);
        assertThat(events.get(1).reason()).contains("admitted after review");
    }

    @Test
    @DisplayName("the ledger is append-only: the previous state can be reconstructed")
    void theLedgerIsAppendOnly(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "x", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));

        quarantine.quarantine("ep-1", "first", 1000L);
        String afterFirst = Files.readString(quarantine.path());
        quarantine.release("ep-1", "second", 2000L);
        String afterSecond = Files.readString(quarantine.path());

        assertThat(afterSecond).startsWith(afterFirst);
        assertThat(afterSecond.lines().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("an absent ledger means nothing is quarantined")
    void anAbsentLedgerMeansNothingIsQuarantined(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);

        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        assertThat(Files.exists(quarantine.path())).isFalse();
        assertThat(quarantine.quarantinedIds()).isEmpty();
        assertThat(new EpisodicLog(logPath).learningCandidates()).hasSize(1);
    }

    @Test
    @DisplayName("a malformed ledger line is counted, not silently dropped")
    void aMalformedLedgerLineIsCounted(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        quarantine.quarantine("ep-1", "probe", 1000L);
        Files.writeString(quarantine.path(), "{\"op\":\"QUARANT\n",
            StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        quarantine.quarantinedIds();
        assertThat(quarantine.lastSkippedLines())
            .as("a quarantine mark that cannot be read is a record that could silently "
                + "re-enter the learning feed: it must be visible").isEqualTo(1);
        assertThat(quarantine.isQuarantined("ep-1")).isTrue();
    }

    @Test
    @DisplayName("the tool marks records without touching the log")
    void theToolMarksWithoutTouchingTheLog(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);
        writeLegacy(logPath, "ep-2", "Moskva stolitsa Rossii?", 0.9, true);
        String bytesBefore = Files.readString(logPath);

        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        // The tool's own selection rule: every record whose input or reply matches
        // the frozen evaluation corpus.
        int marked = 0;
        for (EpisodicLog.Entry e : new EpisodicLog(logPath).readAll()) {
            if (PromotionGate.isFrozenProbe(e.input()) || PromotionGate.isFrozenProbe(e.reply())) {
                quarantine.quarantine(e.id(), "frozen probe", 1000L);
                marked++;
            }
        }

        assertThat(marked).isEqualTo(1);
        assertThat(Files.readString(logPath)).isEqualTo(bytesBefore);
        assertThat(new EpisodicLog(logPath).learningCandidates()).hasSize(1);
        assertThat(new EpisodicLog(logPath).learningWindow().get(0).id()).isEqualTo("ep-1");
    }

    @Test
    @DisplayName("the quarantine is bound to the log without any configuration")
    void theQuarantineIsBoundToItsLog(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        assertThat(log.quarantine().path())
            .isEqualTo(EpisodeQuarantine.defaultLedgerPath(logPath));
        assertThat(log.quarantine().path().getFileName().toString())
            .isEqualTo(EpisodeQuarantine.LEDGER_FILE_NAME);
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F4) — the ledger write is ATOMIC.
     *
     * <p>The defect: the first implementation appended with a plain
     * {@code Files.writeString(..., APPEND)}, so a crash in the middle of that write
     * left a torn line, the torn line was skipped on the next read, and the
     * quarantine mark it carried was silently lost — the record the operator
     * excluded re-entered the learning feed.</p>
     */
    @Test
    @DisplayName("the ledger is never left half-written")
    void theLedgerIsNeverLeftHalfWritten(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));

        for (int i = 0; i < 5; i++) {
            quarantine.quarantine("ep-" + i, "named record", 1000L + i);
        }

        List<String> lines = Files.readAllLines(quarantine.path());
        assertThat(lines).hasSize(5);
        for (String line : lines) {
            assertThat(line)
                .as("every ledger line must be a COMPLETE event")
                .startsWith("{").endsWith("}");
            assertThat(EpisodeQuarantine.Event.fromJson(line))
                .as("every ledger line must parse").isNotNull();
        }
        assertThat(quarantine.lastSkippedLines())
            .as("a ledger this build wrote must be readable in full")
            .isZero();
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F4) — a ledger this build cannot read
     * FAILS CLOSED.
     *
     * <p>An exclusion that cannot be read is not an exclusion. The gate admits
     * nothing while the ledger is malformed, and the count of the unreadable lines
     * is reported rather than swallowed.</p>
     */
    @Test
    @DisplayName("an unreadable ledger fails closed — nothing is admitted")
    void anUnreadableLedgerFailsClosed(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        Files.writeString(quarantine.path(), "{\"op\":\"QUARANT\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        // The ledger's health is the state of its LAST read, so the ledger is read
        // first — exactly as the admission gate reads it.
        quarantine.quarantinedIds();
        assertThat(quarantine.readable())
            .as("the ledger must report that it could not be read in full")
            .isFalse();
        assertThat(quarantine.malformed()).isTrue();
        assertThat(quarantine.lastSkippedLines()).isEqualTo(1);
        assertThat(LearningAdmission.admitted(new EpisodicLog(logPath).readAll(), quarantine))
            .as("an unreadable exclusion is not an exclusion: the gate fails closed")
            .isEmpty();
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F4) — the ledger's health is on a RUNTIME
     * path, so the counter is not dead code.
     */
    @Test
    @DisplayName("the runtime reports that the ledger could not be read")
    void theRuntimeReportsThatTheLedgerCouldNotBeRead(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        Files.writeString(quarantine.path(), "{\"op\":\"QUARANT\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        io.matrix.bir.BirRegistry registry = new io.matrix.bir.BirRegistry();
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                new EpisodicLog(logPath), registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();

            assertThat(dream.quarantineLedgerReadable)
                .as("the operator must be able to see that the ledger was unreadable")
                .isFalse();
            assertThat(dream.quarantineMalformedLines).isEqualTo(1);
            assertThat(dream.episodesAdmitted)
                .as("the gate fails closed: nothing is admitted while the ledger is "
                    + "unreadable")
                .isZero();
            assertThat(dream.snapshot())
                .as("the snapshot is the status endpoint's payload")
                .containsEntry("quarantineLedgerReadable", false)
                .containsEntry("quarantineMalformedLines", 1);
        }
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F5) — the rule names RECORDS.
     *
     * <p>The defect: the tool could quarantine "the records at log positions A..B".
     * A position is not an identity — the same command marks a different set of
     * records as soon as one more interaction is appended, a FRESH ORGANIC one
     * included. The command is gone, and the commands that remain name records.</p>
     */
    @Test
    @DisplayName("there is no command that quarantines by POSITION")
    void thereIsNoCommandThatQuarantinesByPosition(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);

        int code = EpisodeQuarantineTool.run(
            new String[] { "quarantine-range", logPath.toString(), "0", "1" });

        assertThat(code)
            .as("the positional command must be refused: a position is not an identity")
            .isEqualTo(2);
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F5) — the id-based command marks exactly
     * the records it names.
     */
    @Test
    @DisplayName("the id-based command marks the records it names and nothing else")
    void theIdBasedCommandMarksTheRecordsItNames(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic one", 0.9, true);
        writeLegacy(logPath, "ep-2", "organic two", 0.9, true);
        writeLegacy(logPath, "ep-3", "organic three", 0.9, true);
        Path idsFile = dir.resolve("ids.txt");
        Files.writeString(idsFile, "ep-1\nep-3\n", StandardCharsets.UTF_8);

        int code = EpisodeQuarantineTool.run(
            new String[] { "quarantine-ids", logPath.toString(), idsFile.toString() });

        assertThat(code).isZero();
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        assertThat(quarantine.quarantinedIds()).containsExactlyInAnyOrder("ep-1", "ep-3");
        assertThat(new EpisodicLog(logPath).learningCandidates())
            .as("the record that was not named stays learnable")
            .hasSize(1);
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 2) — the ledger's state
     * before the first read is its OWN value.
     *
     * <p>The defect: {@code readable()} reports the state after the LAST read, and
     * there is no last read before the first one — so a caller that asked "has the
     * ledger been looked at?" was told "yes" about a read that never happened. The
     * contract is now explicit: the pre-read state is
     * {@link EpisodeQuarantine.State#NOT_READ_YET}, and a caller that wants to know
     * whether the ledger has been looked at asks {@code state()}, not
     * {@code readable()}.</p>
     */
    @Test
    @DisplayName("before the first read the ledger's state is NOT_READ_YET")
    void beforeTheFirstReadTheStateIsNotReadYet(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));

        assertThat(quarantine.state())
            .as("nobody has looked at the ledger yet, and that is its own state")
            .isEqualTo(EpisodeQuarantine.State.NOT_READ_YET);
        assertThat(quarantine.malformed())
            .as("nothing has been read, so nothing has been found malformed")
            .isFalse();

        // After a read the state says what the read found.
        quarantine.quarantinedIds();
        assertThat(quarantine.state()).isEqualTo(EpisodeQuarantine.State.READABLE);
        assertThat(quarantine.readable()).isTrue();
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 3) — an INEFFECTIVE mark is
     * visible.
     *
     * <p>The defect: a ledger line whose id names no record was accepted silently.
     * Such a mark excludes nothing, and it looks effective — the ledger claims an
     * exclusion that does not exist. The count is now on the runtime path.</p>
     */
    @Test
    @DisplayName("a mark that names no record is counted and reported")
    void aMarkThatNamesNoRecordIsCountedAndReported(@TempDir Path dir) throws Exception {
        Path logPath = dir.resolve("episodic.ndjson");
        writeLegacy(logPath, "ep-1", "organic one", 0.9, true);
        writeLegacy(logPath, "ep-2", "organic two", 0.9, true);
        EpisodeQuarantine quarantine =
            new EpisodeQuarantine(EpisodeQuarantine.defaultLedgerPath(logPath));
        quarantine.quarantine("ep-1", "named record", 1000L);
        // A mark for a record that is not there: syntactically valid, ineffective.
        quarantine.quarantine("ep-does-not-exist", "a mark with no record", 1001L);

        List<EpisodicLog.Entry> all = new EpisodicLog(logPath).readAll();
        assertThat(LearningAdmission.unmatchedQuarantineIds(all, quarantine))
            .as("the ineffective mark must be visible")
            .isEqualTo(1);
        assertThat(new EpisodicLog(logPath).learningCandidates())
            .as("the effective mark still excludes its record")
            .hasSize(1);

        // And the count is on the runtime path.
        io.matrix.bir.BirRegistry registry = new io.matrix.bir.BirRegistry();
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                new EpisodicLog(logPath), registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();

            assertThat(dream.unmatchedQuarantineIds)
                .as("the operator must be able to see that a mark excludes nothing")
                .isEqualTo(1);
            assertThat(dream.notes)
                .as("the report says so in words, not only in a number")
                .anyMatch(n -> n.contains("INEFFECTIVE_MARKS"));
            assertThat(dream.snapshot())
                .containsEntry("unmatchedQuarantineIds", 1);
        }
    }
}
