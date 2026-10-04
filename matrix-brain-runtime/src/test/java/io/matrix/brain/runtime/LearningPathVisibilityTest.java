package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.21 — a silently skipped line in a LEARNING path is a lost lesson.
 *
 * <p>Two of the three sites fixed here are unlike ordinary loaders, because what they
 * drop is not inert data:</p>
 * <ul>
 *   <li>{@link EpisodicLog#readAll()} is what {@link RealSleepScheduler} reads to induce
 *       rules. A dropped episode is a dropped opportunity to learn, with no trace that
 *       the mind ever saw the interaction.</li>
 *   <li>{@link DistillationLedger}'s summary returns {@code runs} and
 *       {@code total_inputs_bytes} together. A silently short byte total looks exactly
 *       like a run that consumed nothing — a claim about work that was done.</li>
 * </ul>
 *
 * <p>Skipping remains correct in both: a torn trailing line is expected in an
 * append-only log, and refusing to read would turn a recoverable file into an unusable
 * one. What was wrong was doing it without saying so, which is the sixth instance of
 * this class in the campaign.</p>
 */
class LearningPathVisibilityTest {

    /** Captures warnings from a logger so "it logged" can be asserted, not assumed. */
    private static List<Level> captureWarnings(Logger logger, Runnable body) {
        List<Level> seen = new java.util.ArrayList<>();
        Handler h = new Handler() {
            @Override public void publish(LogRecord r) { seen.add(r.getLevel()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(h);
        try { body.run(); }
        finally { logger.removeHandler(h); }
        return seen;
    }

    @Test
    void aMalformedEpisodeIsCountedAndReported(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("episodic.ndjson");
        // one good episode, one torn line
        Files.writeString(log,
            "{\"id\":\"ep-1\",\"input\":\"a\",\"reply\":\"b\",\"confidence\":0.5,"
                + "\"accepted\":true,\"modulators\":[],\"ts\":1}\n"
            + "{\"id\":\"ep-2\",\"input\":\"tor\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        var episodic = new EpisodicLog(log);
        // give the store a real entry through the gated path
        var decision = episodic.append("hello", "a reply", 0.6, true, List.of("mat:hdc"));
        assertTrue(decision.promoted(), "precondition: the good episode must be accepted");

        Logger lg = Logger.getLogger(EpisodicLog.class.getName());
        List<Level> warnings = captureWarnings(lg, episodic::readAll);

        assertEquals(1, episodic.lastSkippedLines(),
            "the torn episode must be COUNTED — a dropped episode is a dropped lesson, "
                + "and silence is what made it invisible");
        assertTrue(warnings.contains(Level.WARNING),
            "and it must be logged where an operator can see it");
    }

    @Test
    void aCleanEpisodicLogReportsNoSkips(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("episodic.ndjson");
        var episodic = new EpisodicLog(log);
        episodic.append("hello", "a reply", 0.6, true, List.of("mat:hdc"));
        Logger lg = Logger.getLogger(EpisodicLog.class.getName());
        List<Level> warnings = captureWarnings(lg, episodic::readAll);
        assertEquals(0, episodic.lastSkippedLines(),
            "a clean file must not report skips — a counter that is always positive is a "
                + "counter nobody reads");
        assertTrue(warnings.isEmpty(), "and must not warn, got levels " + warnings);
    }

    @Test
    void aPartialLedgerSummaryIsMarkedPartial(@TempDir Path dir) throws Exception {
        Path ledger = dir.resolve("ledger.ndjson");
        // The real record is 11 fields, so samplesUsed is always followed by another
        // field. My first fixture put it last, which the summary's comma-delimited
        // reader cannot terminate — a fixture error that looked like a code error.
        Files.writeString(ledger,
            "{\"sourceId\":\"a\",\"samplesUsed\":1024,\"inputBits\":4}\n"
            + "{\"sourceId\":\"b\",\"samplesUsed\":NOT_A_NUMBER,\"inputBits\":4}\n",
            StandardCharsets.UTF_8);

        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        java.util.Map<String, Object> summary;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            summary = new DistillationLedger(ledger).summary();
        } finally {
            System.setErr(realErr);
        }
        String out = captured.toString(StandardCharsets.UTF_8);

        assertEquals(Boolean.TRUE, summary.get("partial"),
            "a short total must be marked partial, or it reads as 'consumed nothing'");
        assertEquals(1, summary.get("unparsed_lines"));
        assertTrue(out.contains("PARTIAL"), "and the reason must reach an operator: " + out);
    }

    @Test
    void aCleanLedgerSummaryIsNotMarkedPartial(@TempDir Path dir) throws Exception {
        Path ledger = dir.resolve("ledger.ndjson");
        Files.writeString(ledger,
            "{\"sourceId\":\"a\",\"samplesUsed\":1024,\"inputBits\":4}\n"
            + "{\"sourceId\":\"b\",\"samplesUsed\":2048,\"inputBits\":4}\n",
            StandardCharsets.UTF_8);
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        java.util.Map<String, Object> summary;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            summary = new DistillationLedger(ledger).summary();
        } finally {
            System.setErr(realErr);
        }
        assertEquals(2, summary.get("runs"));
        assertEquals(3072L, summary.get("total_inputs_bytes"));
        assertTrue(!summary.containsKey("partial"),
            "a clean summary must not be flagged, or the flag is noise");
        assertTrue(captured.toString(StandardCharsets.UTF_8).isEmpty());
    }
}
